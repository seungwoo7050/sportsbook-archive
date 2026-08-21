package com.sportsbook.wallet.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.wallet.domain.AdjustmentStatus;
import com.sportsbook.wallet.domain.WalletOperation;
import com.sportsbook.wallet.domain.WalletOperationStatus;
import com.sportsbook.wallet.persistence.AccountRepository;
import com.sportsbook.wallet.persistence.LedgerEntryRepository;
import com.sportsbook.wallet.persistence.WalletAdjustmentRepository;
import com.sportsbook.wallet.persistence.WalletOperationRepository;
import com.sportsbook.wallet.service.command.AdjustmentCommand;
import com.sportsbook.wallet.service.command.DepositCommand;
import com.sportsbook.wallet.service.command.OpenAccountCommand;
import java.math.BigInteger;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = "wallet.outbox.scheduling-enabled=false")
@Testcontainers
class RecoveryWorkerPersistenceTest {
  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired WalletService wallet;
  @Autowired WalletAdjustmentService adjustmentService;
  @Autowired RecoveryWorker worker;
  @Autowired AccountRepository accounts;
  @Autowired WalletAdjustmentRepository adjustments;
  @Autowired WalletOperationRepository operations;
  @Autowired LedgerEntryRepository ledger;

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Test
  void persistsOneFullyFundedRecoveryExactlyOnce() {
    UUID userId = UUID.fromString("019b76da-a000-7000-8000-000000000190");
    UUID revisionId = UUID.fromString("019b76da-a000-7000-8000-000000000191");
    wallet.openAccount(new OpenAccountCommand(userId, Money.krw(0L).currency()));
    wallet.deposit(
        new DepositCommand(userId, Money.krw(200L), IdempotencyKey.of("deposit:recovery-seed")));
    AdjustmentCommand command =
        new AdjustmentCommand(
            revisionId,
            UUID.fromString("019b76da-a000-7000-8000-000000000192"),
            1L,
            userId,
            Money.krw(500L),
            Money.krw(200L),
            IdempotencyKey.of("settlement:revision:" + revisionId));
    assertThat(adjustmentService.adjust(command).status()).isEqualTo(AdjustmentStatus.BLOCKED);
    wallet.deposit(
        new DepositCommand(userId, Money.krw(100L), IdempotencyKey.of("deposit:recovery-wake")));

    assertThat(worker.recoverOne()).isEqualTo(RecoveryWorker.Result.APPLIED);
    assertThat(worker.recoverOne()).isEqualTo(RecoveryWorker.Result.NO_WORK);

    assertThat(adjustments.findById(revisionId).orElseThrow().status())
        .isEqualTo(AdjustmentStatus.APPLIED);
    assertThat(operations.findById(command.idempotencyKey().value()))
        .get()
        .extracting(WalletOperation::status)
        .isEqualTo(WalletOperationStatus.SUCCEEDED);
    assertThat(ledger.findByIdempotencyKey(command.idempotencyKey().value())).hasSize(2);
    assertThat(accounts.findById(userId).orElseThrow())
        .satisfies(
            account -> {
              assertThat(account.available()).isEqualTo(Money.krw(0L));
              assertThat(account.recoveryDebtAmount()).isEqualTo(BigInteger.ZERO);
              assertThat(account.isOutboundFrozen()).isFalse();
            });
  }

  @Test
  void twoWorkersCannotCollectTheSameHead() {
    UUID userId = UUID.fromString("019b76da-a000-7000-8000-000000000193");
    UUID revisionId = UUID.fromString("019b76da-a000-7000-8000-000000000194");
    wallet.openAccount(new OpenAccountCommand(userId, Money.krw(0L).currency()));
    wallet.deposit(
        new DepositCommand(userId, Money.krw(200L), IdempotencyKey.of("deposit:replica-seed")));
    AdjustmentCommand command =
        new AdjustmentCommand(
            revisionId,
            UUID.fromString("019b76da-a000-7000-8000-000000000195"),
            1L,
            userId,
            Money.krw(600L),
            Money.krw(300L),
            IdempotencyKey.of("settlement:revision:" + revisionId));
    adjustmentService.adjust(command);
    wallet.deposit(
        new DepositCommand(userId, Money.krw(100L), IdempotencyKey.of("deposit:replica-wake")));
    CountDownLatch start = new CountDownLatch(1);
    java.util.function.Supplier<RecoveryWorker.Result> recover =
        () -> {
          await(start);
          return worker.recoverOne();
        };
    CompletableFuture<RecoveryWorker.Result> first = CompletableFuture.supplyAsync(recover);
    CompletableFuture<RecoveryWorker.Result> second = CompletableFuture.supplyAsync(recover);

    start.countDown();

    assertThat(java.util.List.of(first.join(), second.join()))
        .containsExactlyInAnyOrder(RecoveryWorker.Result.APPLIED, RecoveryWorker.Result.NO_WORK);
    assertThat(ledger.findByIdempotencyKey(command.idempotencyKey().value())).hasSize(2);
    assertThat(adjustments.findById(revisionId).orElseThrow().status())
        .isEqualTo(AdjustmentStatus.APPLIED);
    assertThat(accounts.findById(userId).orElseThrow().recoveryDebtAmount())
        .isEqualTo(BigInteger.ZERO);
  }

  private static void await(CountDownLatch latch) {
    try {
      latch.await();
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(interrupted);
    }
  }
}
