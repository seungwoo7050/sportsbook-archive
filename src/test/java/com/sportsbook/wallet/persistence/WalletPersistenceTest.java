package com.sportsbook.wallet.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.wallet.config.WalletInfrastructureConfig;
import com.sportsbook.wallet.domain.Account;
import com.sportsbook.wallet.domain.BalanceBucket;
import com.sportsbook.wallet.domain.LedgerEntry;
import com.sportsbook.wallet.domain.SystemAccountIds;
import com.sportsbook.wallet.domain.WalletCaller;
import com.sportsbook.wallet.domain.WalletFailureCode;
import com.sportsbook.wallet.domain.WalletFailureSnapshot;
import com.sportsbook.wallet.domain.WalletOperation;
import com.sportsbook.wallet.domain.WalletOperationKind;
import com.sportsbook.wallet.domain.WalletOperationStatus;
import com.sportsbook.wallet.domain.error.AccountNotFoundException;
import com.sportsbook.wallet.domain.error.CurrencyMismatchException;
import com.sportsbook.wallet.domain.error.IdempotencyConflictException;
import com.sportsbook.wallet.domain.error.WalletBusyException;
import com.sportsbook.wallet.domain.error.WalletRejectedException;
import com.sportsbook.wallet.integrity.OperationCommitted;
import com.sportsbook.wallet.outbox.OutboxAppender;
import com.sportsbook.wallet.service.IdempotencyCache;
import com.sportsbook.wallet.service.WalletOperationExecutor;
import com.sportsbook.wallet.service.WalletOperationResult;
import com.sportsbook.wallet.service.WalletOutcomeResolver;
import com.sportsbook.wallet.service.WalletService;
import com.sportsbook.wallet.service.WalletTransferExecutor;
import com.sportsbook.wallet.service.WalletTransferWriter;
import com.sportsbook.wallet.service.command.CreditCommand;
import com.sportsbook.wallet.service.command.CreditReason;
import com.sportsbook.wallet.service.command.DebitCommand;
import com.sportsbook.wallet.service.command.DepositCommand;
import com.sportsbook.wallet.service.command.OpenAccountCommand;
import com.sportsbook.wallet.service.command.WithdrawCommand;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest(properties = "spring.test.database.replace=NONE")
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({
  IdempotencyKeyLock.class,
  OutboxAppender.class,
  OutboxStreamLock.class,
  WalletInfrastructureConfig.class,
  WalletOperationExecutor.class,
  WalletOutcomeResolver.class,
  WalletService.class,
  WalletTransferExecutor.class,
  WalletTransferWriter.class,
  WalletPersistenceTest.CommitFault.class
})
class WalletPersistenceTest {
  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired JdbcTemplate jdbc;
  @Autowired AccountRepository accounts;
  @Autowired LedgerEntryRepository ledger;
  @Autowired WalletOperationRepository operations;
  @SpyBean OutboxEventRepository outboxEvents;
  @Autowired IdempotencyKeyLock idempotencyLocks;
  @Autowired WalletService wallet;
  @Autowired CommitFault commitFault;
  @MockBean IdempotencyCache cache;
  @Autowired javax.sql.DataSource dataSource;
  @Autowired org.springframework.transaction.PlatformTransactionManager transactions;

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Test
  void migratesFinalConstraintsAndAdjustmentReason() {
    UUID userId = UUID.fromString("019b76da-a000-7000-8000-000000000010");
    jdbc.update(
        """
        INSERT INTO account (
            user_id, available_currency, locked_currency, created_at, updated_at
        ) VALUES (?, 'KRW', 'KRW', now(), now())
        """,
        userId);
    jdbc.update(
        """
        INSERT INTO ledger_entry (
            entry_id, account_id, bucket, side, amount, currency, reason,
            idempotency_key, operation_group_id, created_at
        ) VALUES (?, ?, 'AVAILABLE', 'DEBIT', 1, 'KRW', 'BET_ADJUSTMENT', ?, ?, now())
        """,
        UUID.randomUUID(),
        userId,
        "adjustment:persistence",
        UUID.randomUUID());

    BigInteger debt = BigInteger.ONE.shiftLeft(Long.SIZE - 1);
    jdbc.update(
        "UPDATE account SET recovery_debt_amount=?, recovery_frozen_at=now() WHERE user_id=?",
        debt,
        userId);
    jdbc.update(
        "UPDATE account SET updated_at=created_at - interval '1 second' WHERE user_id=?", userId);

    assertThat(
            jdbc.queryForObject(
                    "SELECT recovery_debt_amount FROM account WHERE user_id=?",
                    BigDecimal.class,
                    userId)
                .toBigIntegerExact())
        .isEqualTo(debt);
    assertThat(
            jdbc.queryForObject(
                "SELECT reason FROM ledger_entry WHERE idempotency_key=?",
                String.class,
                "adjustment:persistence"))
        .isEqualTo("BET_ADJUSTMENT");
    assertThat(
            jdbc.queryForObject(
                "SELECT updated_at < created_at FROM account WHERE user_id=?",
                Boolean.class,
                userId))
        .isTrue();
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE account SET available_amount=?, locked_amount=? WHERE user_id=?",
                    Long.MAX_VALUE,
                    1L,
                    userId))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }

  @Test
  void preventsAConcurrentWriterFromTakingTheAccountLock() throws Exception {
    UUID userId = UUID.fromString("019b76da-a000-7000-8000-000000000011");
    accounts.saveAndFlush(Account.openFor(userId, Money.krw(0L).currency(), Instant.now()));
    var status = transactions.getTransaction(new DefaultTransactionDefinition());
    try {
      accounts.findByUserIdForUpdate(userId).orElseThrow();
      try (var connection = dataSource.getConnection()) {
        connection.setAutoCommit(false);
        try (var timeout = connection.createStatement();
            var lock =
                connection.prepareStatement(
                    "SELECT user_id FROM account WHERE user_id = ? FOR UPDATE")) {
          timeout.execute("SET LOCAL lock_timeout = '100ms'");
          lock.setObject(1, userId);
          assertThatThrownBy(lock::executeQuery).isInstanceOf(java.sql.SQLException.class);
        }
      }
    } finally {
      transactions.rollback(status);
    }
  }

  @Test
  void queriesLedgerPairsByIdempotencyKeyAndOperationGroup() {
    UUID userId = UUID.fromString("019b76da-a000-7000-8000-000000000012");
    UUID groupId = UUID.fromString("019b76da-a000-7000-8000-000000000013");
    IdempotencyKey key = IdempotencyKey.of("ledger:query-pair");
    LedgerEntry.Pair pair =
        LedgerEntry.pair(
            new LedgerEntry.TransferLeg(userId, BalanceBucket.AVAILABLE),
            new LedgerEntry.TransferLeg(SystemAccountIds.EXTERNAL_PAYMENT, BalanceBucket.AVAILABLE),
            Money.krw(25L),
            com.sportsbook.wallet.domain.LedgerReason.DEPOSIT,
            key,
            groupId,
            Instant.parse("2026-01-01T00:00:00Z"));

    ledger.saveAllAndFlush(java.util.List.of(pair.debit(), pair.credit()));

    assertThat(ledger.findByIdempotencyKey(key.value()))
        .extracting(LedgerEntry::entryId)
        .containsExactlyInAnyOrder(pair.debit().entryId(), pair.credit().entryId());
    assertThat(ledger.findByOperationGroupId(groupId))
        .extracting(LedgerEntry::entryId)
        .containsExactlyInAnyOrder(pair.debit().entryId(), pair.credit().entryId());
  }

  @Test
  void roundTripsSucceededRejectedAndBlockedWalletOutcomes() {
    UUID userId = UUID.fromString("019b76da-a000-7000-8000-000000000014");
    Instant now = Instant.parse("2026-01-02T00:00:00Z");
    IdempotencyKey successKey = IdempotencyKey.of("outcome:success");
    IdempotencyKey rejectedKey = IdempotencyKey.of("outcome:rejected");
    IdempotencyKey blockedKey = IdempotencyKey.of("outcome:blocked");
    UUID groupId = UUID.fromString("019b76da-a000-7000-8000-000000000015");
    WalletFailureSnapshot failure =
        WalletFailureSnapshot.withBalance(
            WalletFailureCode.INSUFFICIENT_BALANCE, "available 20", Money.krw(20L));

    operations.saveAllAndFlush(
        java.util.List.of(
            WalletOperation.succeeded(
                successKey,
                WalletCaller.PLATFORM,
                WalletOperationKind.DEPOSIT,
                userId,
                Money.krw(100L),
                "a".repeat(64),
                groupId,
                now),
            WalletOperation.rejected(
                rejectedKey,
                WalletCaller.PLATFORM,
                WalletOperationKind.WITHDRAW,
                userId,
                Money.krw(30L),
                "b".repeat(64),
                failure,
                now),
            WalletOperation.blockedFunds(
                blockedKey, WalletCaller.SETTLEMENT, userId, Money.krw(40L), "c".repeat(64), now)));

    assertThat(
            jdbc.queryForList(
                "SELECT status FROM wallet_operation ORDER BY idempotency_key", String.class))
        .containsExactlyInAnyOrder("SUCCEEDED", "REJECTED", "BLOCKED_FUNDS");
    assertThat(operations.findById(successKey.value()).orElseThrow().operationGroupId())
        .isEqualTo(groupId);
    WalletOperation rejected = operations.findById(rejectedKey.value()).orElseThrow();
    assertThat(rejected.status()).isEqualTo(WalletOperationStatus.REJECTED);
    assertThat(rejected.failure().code()).isEqualTo(WalletFailureCode.INSUFFICIENT_BALANCE);
    assertThat(rejected.failure().balance()).isEqualTo(Money.krw(20L));
    assertThat(operations.findById(blockedKey.value()).orElseThrow().status())
        .isEqualTo(WalletOperationStatus.BLOCKED_FUNDS);
  }

  @Test
  void timesOutAContendedKeyWithoutClaimingItsOutcome() {
    IdempotencyKey key = IdempotencyKey.of("busy:unclaimed");
    var owner = transactions.getTransaction(new DefaultTransactionDefinition());
    try {
      idempotencyLocks.acquire(key);
      CompletableFuture<Void> waiter =
          CompletableFuture.runAsync(
              () ->
                  new org.springframework.transaction.support.TransactionTemplate(transactions)
                      .executeWithoutResult(
                          ignored -> {
                            jdbc.execute("SET LOCAL lock_timeout = '100ms'");
                            idempotencyLocks.acquire(key);
                          }));

      assertThatThrownBy(waiter::join).hasCauseInstanceOf(WalletBusyException.class);
      assertThat(operations.findById(key.value())).isEmpty();
    } finally {
      transactions.rollback(owner);
    }
  }

  @Test
  void mapsDifferentKeyAccountContentionWithoutClaimingAnOutcome() {
    UUID userId = UUID.fromString("019b76da-a000-7000-8000-00000000001b");
    IdempotencyKey ownerKey = IdempotencyKey.of("busy:account-owner");
    IdempotencyKey waiterKey = IdempotencyKey.of("busy:account-waiter");
    accounts.saveAndFlush(Account.openFor(userId, Money.krw(0L).currency(), Instant.now()));
    var owner = transactions.getTransaction(new DefaultTransactionDefinition());
    try {
      idempotencyLocks.acquire(ownerKey);
      accounts.findByUserIdForUpdate(userId).orElseThrow();
      CompletableFuture<Void> waiter =
          CompletableFuture.runAsync(
              () ->
                  new org.springframework.transaction.support.TransactionTemplate(transactions)
                      .executeWithoutResult(
                          ignored -> {
                            jdbc.execute("SET LOCAL lock_timeout = '100ms'");
                            idempotencyLocks.acquire(waiterKey);
                            try {
                              accounts.findByUserIdForUpdate(userId).orElseThrow();
                            } catch (RuntimeException failure) {
                              throw PostgresFailureTranslator.translate(waiterKey, failure);
                            }
                          }));

      assertThatThrownBy(waiter::join).hasCauseInstanceOf(WalletBusyException.class);
      assertThat(operations.findAllById(java.util.List.of(ownerKey.value(), waiterKey.value())))
          .isEmpty();
    } finally {
      transactions.rollback(owner);
    }
  }

  @Test
  void reusesAnAccountOnlyForTheSameCurrency() {
    UUID userId = UUID.fromString("019b76da-a000-7000-8000-000000000012");
    OpenAccountCommand krw =
        new OpenAccountCommand(userId, com.sportsbook.protocol.value.Currency.KRW);

    Account first = wallet.openAccount(krw);
    Account replay = wallet.openAccount(krw);

    assertThat(replay.userId()).isEqualTo(first.userId());
    assertThat(accounts.count()).isGreaterThanOrEqualTo(1L);
    assertThatThrownBy(
            () ->
                wallet.openAccount(
                    new OpenAccountCommand(userId, com.sportsbook.protocol.value.Currency.USD)))
        .isInstanceOf(CurrencyMismatchException.class);
  }

  @Test
  void rejectsMissingAccountsWithTheirExactIdentity() {
    UUID missingUserId = UUID.randomUUID();

    assertThatThrownBy(() -> wallet.requireAccount(missingUserId))
        .isInstanceOfSatisfying(
            AccountNotFoundException.class,
            missing -> assertThat(missing.userId()).isEqualTo(missingUserId));
  }

  @Test
  void convergesConcurrentAccountOpeners() {
    UUID userId = UUID.fromString("019b76da-a000-7000-8000-000000000013");
    OpenAccountCommand command =
        new OpenAccountCommand(userId, com.sportsbook.protocol.value.Currency.KRW);
    java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
    CompletableFuture<Account> first =
        CompletableFuture.supplyAsync(
            () -> {
              await(start);
              return wallet.openAccount(command);
            });
    CompletableFuture<Account> second =
        CompletableFuture.supplyAsync(
            () -> {
              await(start);
              return wallet.openAccount(command);
            });

    start.countDown();

    assertThat(first.join().userId()).isEqualTo(userId);
    assertThat(second.join().userId()).isEqualTo(userId);
    assertThat(accounts.findAll().stream().filter(account -> account.userId().equals(userId)))
        .hasSize(1);
  }

  @Test
  void commitsAndExactlyReplaysOneDeposit() {
    UUID userId = UUID.fromString("019b76da-a000-7000-8000-000000000014");
    wallet.openAccount(new OpenAccountCommand(userId, com.sportsbook.protocol.value.Currency.KRW));
    DepositCommand command =
        new DepositCommand(userId, Money.krw(500L), IdempotencyKey.of("deposit:durable"));

    var first = wallet.deposit(command);
    var replay = wallet.deposit(command);

    assertThat(replay).isEqualTo(first);
    assertThat(wallet.requireAccount(userId).available()).isEqualTo(Money.krw(500L));
    assertThat(ledger.findByIdempotencyKey(command.idempotencyKey().value())).hasSize(2);
    assertThat(operations.findById(command.idempotencyKey().value()).orElseThrow().status())
        .isEqualTo(WalletOperationStatus.SUCCEEDED);
    assertThatThrownBy(
            () ->
                wallet.deposit(
                    new DepositCommand(userId, Money.krw(501L), command.idempotencyKey())))
        .isInstanceOf(IdempotencyConflictException.class);
  }

  @Test
  void replaysACommittedRejectionAfterFactsChange() {
    UUID userId = UUID.fromString("019b76da-a000-7000-8000-000000000015");
    DepositCommand command =
        new DepositCommand(userId, Money.krw(100L), IdempotencyKey.of("deposit:missing"));

    WalletRejectedException first =
        catchThrowableOfType(() -> wallet.deposit(command), WalletRejectedException.class);
    wallet.openAccount(new OpenAccountCommand(userId, com.sportsbook.protocol.value.Currency.KRW));
    WalletRejectedException replay =
        catchThrowableOfType(() -> wallet.deposit(command), WalletRejectedException.class);

    assertThat(replay.failure().code()).isEqualTo(WalletFailureCode.ACCOUNT_NOT_FOUND);
    assertThat(replay.failure().detail()).isEqualTo(first.failure().detail());
    assertThat(wallet.requireAccount(userId).available()).isEqualTo(Money.krw(0L));
    assertThat(ledger.findByIdempotencyKey(command.idempotencyKey().value())).isEmpty();
    assertThat(operations.findById(command.idempotencyKey().value()).orElseThrow().status())
        .isEqualTo(WalletOperationStatus.REJECTED);
  }

  @Test
  void rollsBackOutcomeLedgerAndBalanceWhenTransferFails() {
    UUID userId = UUID.fromString("019b76da-a000-7000-8000-00000000001a");
    wallet.openAccount(new OpenAccountCommand(userId, com.sportsbook.protocol.value.Currency.KRW));
    DepositCommand command =
        new DepositCommand(userId, Money.krw(60L), IdempotencyKey.of("deposit:rollback"));
    commitFault.failNext();

    assertThatThrownBy(() -> wallet.deposit(command))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("injected commit notification failure");

    assertThat(wallet.requireAccount(userId).available()).isEqualTo(Money.krw(0L));
    assertThat(ledger.findByIdempotencyKey(command.idempotencyKey().value())).isEmpty();
    assertThat(operations.findById(command.idempotencyKey().value())).isEmpty();
    wallet.deposit(command);
    assertThat(wallet.requireAccount(userId).available()).isEqualTo(Money.krw(60L));
  }

  static final class CommitFault {
    private final AtomicBoolean failNext = new AtomicBoolean();

    void failNext() {
      failNext.set(true);
    }

    @EventListener
    public void afterTransfer(OperationCommitted ignored) {
      if (failNext.compareAndSet(true, false)) {
        throw new IllegalStateException("injected commit notification failure");
      }
    }
  }

  @Test
  void exactlyReplaysWithdrawalSuccessAndInsufficientFunds() {
    UUID userId = UUID.fromString("019b76da-a000-7000-8000-000000000016");
    wallet.openAccount(new OpenAccountCommand(userId, com.sportsbook.protocol.value.Currency.KRW));
    wallet.deposit(
        new DepositCommand(userId, Money.krw(100L), IdempotencyKey.of("deposit:withdraw-seed")));
    WithdrawCommand rejected =
        new WithdrawCommand(userId, Money.krw(200L), IdempotencyKey.of("withdraw:rejected"));

    WalletRejectedException first =
        catchThrowableOfType(() -> wallet.withdraw(rejected), WalletRejectedException.class);
    wallet.deposit(
        new DepositCommand(userId, Money.krw(300L), IdempotencyKey.of("deposit:withdraw-more")));
    WalletRejectedException replay =
        catchThrowableOfType(() -> wallet.withdraw(rejected), WalletRejectedException.class);

    assertThat(first.failure().code()).isEqualTo(WalletFailureCode.INSUFFICIENT_BALANCE);
    assertThat(replay.failure().detail()).isEqualTo(first.failure().detail());
    WithdrawCommand success =
        new WithdrawCommand(userId, Money.krw(150L), IdempotencyKey.of("withdraw:success"));
    var result = wallet.withdraw(success);
    assertThat(wallet.withdraw(success)).isEqualTo(result);
    assertThat(wallet.requireAccount(userId).available()).isEqualTo(Money.krw(250L));
    assertThat(ledger.findByIdempotencyKey(success.idempotencyKey().value())).hasSize(2);
  }

  @Test
  void recoversAfterCacheEvictionOutageAndPostCommitFaults() {
    UUID userId = UUID.fromString("019b76da-a000-7000-8000-000000000017");
    wallet.openAccount(new OpenAccountCommand(userId, com.sportsbook.protocol.value.Currency.KRW));
    DepositCommand command =
        new DepositCommand(userId, Money.krw(90L), IdempotencyKey.of("deposit:cache-fault"));
    doThrow(new org.springframework.data.redis.RedisConnectionFailureException("post-commit"))
        .when(cache)
        .mark(command.idempotencyKey());

    var committed = wallet.deposit(command);
    reset(cache);
    var afterEviction = wallet.deposit(command);
    when(cache.mightContain(command.idempotencyKey()))
        .thenThrow(new org.springframework.data.redis.RedisConnectionFailureException("lookup"));
    var duringOutage = wallet.deposit(command);

    assertThat(afterEviction).isEqualTo(committed);
    assertThat(duringOutage).isEqualTo(committed);
    assertThat(wallet.requireAccount(userId).available()).isEqualTo(Money.krw(90L));
    assertThat(ledger.findByIdempotencyKey(command.idempotencyKey().value())).hasSize(2);
  }

  @Test
  void serializesTwoExactBalanceBetDebits() {
    UUID userId = UUID.fromString("019b76da-a000-7000-8000-000000000018");
    wallet.openAccount(new OpenAccountCommand(userId, com.sportsbook.protocol.value.Currency.KRW));
    wallet.deposit(
        new DepositCommand(userId, Money.krw(100L), IdempotencyKey.of("deposit:race-seed")));
    DebitCommand firstCommand =
        new DebitCommand(userId, Money.krw(100L), IdempotencyKey.of("debit:race:first"));
    DebitCommand secondCommand =
        new DebitCommand(userId, Money.krw(100L), IdempotencyKey.of("debit:race:second"));
    java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
    java.util.function.Function<DebitCommand, WalletOperationStatus> attempt =
        command -> {
          try {
            wallet.debit(command);
            return WalletOperationStatus.SUCCEEDED;
          } catch (WalletRejectedException rejected) {
            return WalletOperationStatus.REJECTED;
          }
        };
    java.util.function.Function<DebitCommand, CompletableFuture<Optional<WalletOperationStatus>>>
        submit =
            command ->
                CompletableFuture.supplyAsync(
                    () -> {
                      await(start);
                      return retryableAttempt(() -> attempt.apply(command));
                    });
    var commands = List.of(firstCommand, secondCommand);
    var attempts = commands.stream().map(submit).toList();

    start.countDown();
    CompletableFuture.allOf(attempts.toArray(CompletableFuture[]::new)).join();
    var statuses =
        java.util.stream.IntStream.range(0, commands.size())
            .mapToObj(
                index ->
                    attempts.get(index).join().orElseGet(() -> attempt.apply(commands.get(index))))
            .toList();

    assertThat(statuses)
        .containsExactlyInAnyOrder(WalletOperationStatus.SUCCEEDED, WalletOperationStatus.REJECTED);
    assertThat(wallet.requireAccount(userId).available()).isEqualTo(Money.krw(0L));
    assertThat(wallet.requireAccount(userId).locked()).isEqualTo(Money.krw(100L));
    assertThat(
            commands.stream()
                .map(command -> operations.findById(command.idempotencyKey().value()).orElseThrow())
                .map(WalletOperation::status))
        .containsExactlyInAnyOrder(WalletOperationStatus.SUCCEEDED, WalletOperationStatus.REJECTED);
    assertThat(
            commands.stream()
                .mapToInt(
                    command -> ledger.findByIdempotencyKey(command.idempotencyKey().value()).size())
                .sum())
        .isEqualTo(2);
  }

  private static <T> Optional<T> retryableAttempt(Supplier<T> attempt) {
    try {
      return Optional.of(attempt.get());
    } catch (WalletBusyException busy) {
      return Optional.empty();
    }
  }

  @Test
  void exactlyReplaysDebitSuccessAndItsSingleOutboxMessage() {
    UUID userId = UUID.fromString("019b76da-a000-7000-8000-00000000001b");
    wallet.openAccount(new OpenAccountCommand(userId, com.sportsbook.protocol.value.Currency.KRW));
    wallet.deposit(
        new DepositCommand(userId, Money.krw(100L), IdempotencyKey.of("deposit:debit-success")));
    DebitCommand command =
        new DebitCommand(userId, Money.krw(60L), IdempotencyKey.of("debit:success"));

    var first = wallet.debit(command);
    var replay = wallet.debit(command);

    assertThat(replay).isEqualTo(first);
    assertThat(wallet.requireAccount(userId).available()).isEqualTo(Money.krw(40L));
    assertThat(wallet.requireAccount(userId).locked()).isEqualTo(Money.krw(60L));
    assertThat(ledger.findByIdempotencyKey(command.idempotencyKey().value())).hasSize(2);
    assertThat(outboxFor(command.idempotencyKey()))
        .singleElement()
        .extracting(com.sportsbook.wallet.outbox.OutboxEvent::topic)
        .isEqualTo(com.sportsbook.wallet.outbox.WalletEventFactory.DEBITED_TOPIC);
  }

  @Test
  void debitsReferenceTheUserLockedLedgerRowRatherThanTheOperationGroup() {
    UUID userId = UUID.fromString("019b76da-a000-7000-8000-000000000023");
    wallet.openAccount(new OpenAccountCommand(userId, com.sportsbook.protocol.value.Currency.KRW));
    wallet.deposit(
        new DepositCommand(userId, Money.krw(100L), IdempotencyKey.of("deposit:debit-proof")));
    DebitCommand command =
        new DebitCommand(userId, Money.krw(60L), IdempotencyKey.of("debit:ledger-proof"));

    var result = wallet.debit(command);
    var message = outboxFor(command.idempotencyKey()).get(0);
    var event =
        com.sportsbook.wallet.outbox.AvroSerializer.deserialize(
            message.payload(), com.sportsbook.protocol.event.WalletDebited.class);
    LedgerEntry userSide =
        ledger.findByIdempotencyKey(command.idempotencyKey().value()).stream()
            .filter(entry -> entry.side() == com.sportsbook.wallet.domain.LedgerSide.DEBIT)
            .findFirst()
            .orElseThrow();

    assertThat(userSide.accountId()).isEqualTo(userId);
    assertThat(userSide.bucket()).isEqualTo(BalanceBucket.LOCKED);
    assertThat(event.getLedgerTxId()).isEqualTo(userSide.entryId().toString());
    assertThat(event.getLedgerTxId()).isNotEqualTo(result.operationGroupId().toString());
  }

  @Test
  void exactlyReplaysATerminalDebitFailureAfterFundsArrive() {
    UUID userId = UUID.fromString("019b76da-a000-7000-8000-00000000001c");
    wallet.openAccount(new OpenAccountCommand(userId, com.sportsbook.protocol.value.Currency.KRW));
    DebitCommand command =
        new DebitCommand(userId, Money.krw(60L), IdempotencyKey.of("debit:terminal-failure"));

    WalletRejectedException first =
        catchThrowableOfType(() -> wallet.debit(command), WalletRejectedException.class);
    wallet.deposit(
        new DepositCommand(userId, Money.krw(100L), IdempotencyKey.of("deposit:after-rejection")));
    WalletRejectedException replay =
        catchThrowableOfType(() -> wallet.debit(command), WalletRejectedException.class);

    assertThat(replay.failure().code()).isEqualTo(WalletFailureCode.INSUFFICIENT_BALANCE);
    assertThat(replay.failure().detail()).isEqualTo(first.failure().detail());
    assertThat(wallet.requireAccount(userId).available()).isEqualTo(Money.krw(100L));
    assertThat(wallet.requireAccount(userId).locked()).isEqualTo(Money.krw(0L));
    assertThat(ledger.findByIdempotencyKey(command.idempotencyKey().value())).isEmpty();
    assertThat(outboxFor(command.idempotencyKey()))
        .singleElement()
        .extracting(com.sportsbook.wallet.outbox.OutboxEvent::topic)
        .isEqualTo(com.sportsbook.wallet.outbox.WalletEventFactory.DEBIT_FAILED_TOPIC);
  }

  @Test
  void refundsLockedStakesAndPaysHouseFundedWinnings() {
    UUID refundUser = UUID.fromString("019b76da-a000-7000-8000-00000000001e");
    wallet.openAccount(
        new OpenAccountCommand(refundUser, com.sportsbook.protocol.value.Currency.KRW));
    wallet.deposit(
        new DepositCommand(refundUser, Money.krw(100L), IdempotencyKey.of("deposit:credit-seed")));
    wallet.debit(
        new DebitCommand(refundUser, Money.krw(100L), IdempotencyKey.of("debit:credit-seed")));
    CreditCommand bettingRefund =
        new CreditCommand(
            refundUser,
            Money.krw(40L),
            CreditCommand.Source.USER_LOCKED,
            CreditReason.REFUND,
            IdempotencyKey.of("credit:betting-refund"));
    CreditCommand settlementVoid =
        new CreditCommand(
            refundUser,
            Money.krw(10L),
            CreditCommand.Source.USER_LOCKED,
            CreditReason.VOID,
            IdempotencyKey.of("credit:settlement-void"));

    var refund = wallet.credit(com.sportsbook.wallet.domain.WalletCaller.BETTING, bettingRefund);
    var voided =
        wallet.credit(com.sportsbook.wallet.domain.WalletCaller.SETTLEMENT, settlementVoid);

    assertThat(wallet.credit(com.sportsbook.wallet.domain.WalletCaller.BETTING, bettingRefund))
        .isEqualTo(refund);
    assertThat(wallet.credit(com.sportsbook.wallet.domain.WalletCaller.SETTLEMENT, settlementVoid))
        .isEqualTo(voided);
    assertThat(wallet.requireAccount(refundUser).available()).isEqualTo(Money.krw(50L));
    assertThat(wallet.requireAccount(refundUser).locked()).isEqualTo(Money.krw(50L));
    assertThat(refund.reason()).isEqualTo(com.sportsbook.wallet.domain.LedgerReason.BET_REFUND);

    UUID payoutUser = UUID.fromString("019b76da-a000-7000-8000-00000000001f");
    wallet.openAccount(
        new OpenAccountCommand(payoutUser, com.sportsbook.protocol.value.Currency.KRW));
    CreditCommand payout =
        new CreditCommand(
            payoutUser,
            Money.krw(250L),
            CreditCommand.Source.HOUSE_POOL,
            CreditReason.PAYOUT,
            IdempotencyKey.of("credit:settlement-payout"));
    var paid = wallet.credit(com.sportsbook.wallet.domain.WalletCaller.SETTLEMENT, payout);

    assertThat(wallet.credit(com.sportsbook.wallet.domain.WalletCaller.SETTLEMENT, payout))
        .isEqualTo(paid);
    assertThat(wallet.requireAccount(payoutUser).available()).isEqualTo(Money.krw(250L));
    assertThat(paid.reason()).isEqualTo(com.sportsbook.wallet.domain.LedgerReason.BET_PAYOUT);
    assertThat(outboxFor(payout.idempotencyKey())).hasSize(1);

    CreditCommand changedReason =
        new CreditCommand(
            refundUser,
            settlementVoid.amount(),
            settlementVoid.source(),
            CreditReason.REFUND,
            settlementVoid.idempotencyKey());
    assertThatThrownBy(
            () ->
                wallet.credit(com.sportsbook.wallet.domain.WalletCaller.SETTLEMENT, changedReason))
        .isInstanceOf(IdempotencyConflictException.class);
    assertThatThrownBy(
            () ->
                wallet.credit(
                    com.sportsbook.wallet.domain.WalletCaller.BETTING,
                    new CreditCommand(
                        refundUser,
                        Money.krw(1L),
                        CreditCommand.Source.HOUSE_POOL,
                        CreditReason.PAYOUT,
                        IdempotencyKey.of("credit:forbidden"))))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void convergesOneHundredConcurrentRequestsForOneKey() {
    UUID userId = UUID.fromString("019b76da-a000-7000-8000-000000000019");
    wallet.openAccount(new OpenAccountCommand(userId, com.sportsbook.protocol.value.Currency.KRW));
    DepositCommand command =
        new DepositCommand(userId, Money.krw(77L), IdempotencyKey.of("deposit:hundred-race"));
    var start = new java.util.concurrent.CountDownLatch(1);
    var pool = java.util.concurrent.Executors.newFixedThreadPool(20);
    try {
      var attempts =
          java.util.stream.IntStream.range(0, 100)
              .mapToObj(
                  ignored ->
                      CompletableFuture.supplyAsync(
                          () -> {
                            await(start);
                            return retryableAttempt(() -> wallet.deposit(command));
                          },
                          pool))
              .toList();
      start.countDown();
      CompletableFuture.allOf(attempts.toArray(CompletableFuture[]::new)).join();

      var initial = attempts.stream().map(CompletableFuture::join).toList();
      var converged =
          initial.stream()
              .map(outcome -> outcome.orElseGet(() -> wallet.deposit(command)))
              .toList();
      WalletOperationResult winner = converged.get(0);
      assertThat(converged).hasSize(100).containsOnly(winner);
      assertThat(operations.findById(command.idempotencyKey().value()))
          .get()
          .satisfies(
              operation -> {
                assertThat(operation.status()).isEqualTo(WalletOperationStatus.SUCCEEDED);
                assertThat(operation.operationGroupId()).isEqualTo(winner.operationGroupId());
              });
    } finally {
      pool.shutdownNow();
    }
    assertThat(wallet.requireAccount(userId).available()).isEqualTo(Money.krw(77L));
    assertThat(ledger.findByIdempotencyKey(command.idempotencyKey().value())).hasSize(2);
  }

  @Test
  void rollsBackDebitWhenOutboxPersistenceFails() {
    UUID userId = UUID.fromString("019b76da-a000-7000-8000-00000000007f");
    wallet.openAccount(new OpenAccountCommand(userId, com.sportsbook.protocol.value.Currency.KRW));
    wallet.deposit(
        new DepositCommand(userId, Money.krw(100L), IdempotencyKey.of("deposit:outbox-failure")));
    DebitCommand command =
        new DebitCommand(userId, Money.krw(60L), IdempotencyKey.of("debit:outbox-failure"));
    doThrow(new IllegalStateException("outbox persistence unavailable"))
        .when(outboxEvents)
        .save(any(com.sportsbook.wallet.outbox.OutboxEvent.class));

    assertThatThrownBy(() -> wallet.debit(command))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("outbox persistence unavailable");

    assertThat(wallet.requireAccount(userId).available()).isEqualTo(Money.krw(100L));
    assertThat(wallet.requireAccount(userId).locked()).isEqualTo(Money.krw(0L));
    assertThat(ledger.findByIdempotencyKey(command.idempotencyKey().value())).isEmpty();
    assertThat(operations.findById(command.idempotencyKey().value())).isEmpty();
    assertThat(outboxFor(command.idempotencyKey())).isEmpty();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM outbox_stream WHERE topic=? AND partition_key=?",
                Integer.class,
                com.sportsbook.wallet.outbox.WalletEventFactory.DEBITED_TOPIC,
                userId.toString()))
        .isZero();
    reset(outboxEvents);
    wallet.debit(command);
    assertThat(outboxFor(command.idempotencyKey()))
        .singleElement()
        .extracting(com.sportsbook.wallet.outbox.OutboxEvent::streamSequence)
        .isEqualTo(1L);
  }

  @Test
  void serializesOneHundredConcurrentBusinessRejections() {
    UUID userId = UUID.fromString("019b76da-a000-7000-8000-00000000001d");
    wallet.openAccount(new OpenAccountCommand(userId, com.sportsbook.protocol.value.Currency.KRW));
    DebitCommand command =
        new DebitCommand(userId, Money.krw(77L), IdempotencyKey.of("debit:hundred-rejections"));
    var start = new java.util.concurrent.CountDownLatch(1);
    var pool = java.util.concurrent.Executors.newFixedThreadPool(20);
    Supplier<WalletRejectedException> reject =
        () -> {
          try {
            wallet.debit(command);
            throw new AssertionError("Debit unexpectedly succeeded");
          } catch (WalletRejectedException rejected) {
            return rejected;
          }
        };
    try {
      var attempts =
          java.util.stream.IntStream.range(0, 100)
              .mapToObj(
                  ignored ->
                      CompletableFuture.supplyAsync(
                          () -> {
                            await(start);
                            return retryableAttempt(reject);
                          },
                          pool))
              .toList();
      start.countDown();
      CompletableFuture.allOf(attempts.toArray(CompletableFuture[]::new)).join();

      var initial = attempts.stream().map(CompletableFuture::join).toList();
      var converged = initial.stream().map(outcome -> outcome.orElseGet(reject)).toList();
      WalletRejectedException winner = converged.get(0);
      assertThat(converged)
          .hasSize(100)
          .allSatisfy(
              rejection ->
                  assertThat(rejection.failure())
                      .usingRecursiveComparison()
                      .isEqualTo(winner.failure()));
      assertThat(operations.findById(command.idempotencyKey().value()))
          .get()
          .satisfies(
              operation -> {
                assertThat(operation.status()).isEqualTo(WalletOperationStatus.REJECTED);
                assertThat(operation.failure())
                    .usingRecursiveComparison()
                    .isEqualTo(winner.failure());
              });
    } finally {
      pool.shutdownNow();
    }
    assertThat(wallet.requireAccount(userId).available()).isEqualTo(Money.krw(0L));
    assertThat(wallet.requireAccount(userId).locked()).isEqualTo(Money.krw(0L));
    assertThat(ledger.findByIdempotencyKey(command.idempotencyKey().value())).isEmpty();
    assertThat(outboxFor(command.idempotencyKey())).hasSize(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT last_sequence FROM outbox_stream WHERE topic=? AND partition_key=?",
                Long.class,
                com.sportsbook.wallet.outbox.WalletEventFactory.DEBIT_FAILED_TOPIC,
                userId.toString()))
        .isEqualTo(1L);
    assertThat(operations.findById(command.idempotencyKey().value()))
        .get()
        .extracting(WalletOperation::status)
        .isEqualTo(WalletOperationStatus.REJECTED);
  }

  private static void await(java.util.concurrent.CountDownLatch latch) {
    try {
      latch.await();
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(interrupted);
    }
  }

  private java.util.List<com.sportsbook.wallet.outbox.OutboxEvent> outboxFor(IdempotencyKey key) {
    return outboxEvents.findAll().stream()
        .filter(event -> event.operationKey().equals(key.value()))
        .toList();
  }
}
