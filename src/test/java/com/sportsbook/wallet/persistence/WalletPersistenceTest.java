package com.sportsbook.wallet.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.wallet.domain.Account;
import com.sportsbook.wallet.domain.BalanceBucket;
import com.sportsbook.wallet.domain.LedgerEntry;
import com.sportsbook.wallet.domain.SystemAccountIds;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
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
class WalletPersistenceTest {
  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired JdbcTemplate jdbc;
  @Autowired AccountRepository accounts;
  @Autowired LedgerEntryRepository ledger;
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
}
