package com.sportsbook.wallet.integrity;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest(properties = "spring.test.database.replace=NONE")
@Testcontainers
@Import(AccountIntegrityRepository.class)
class AccountIntegrityRepositoryTest {
  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired JdbcTemplate jdbc;
  @Autowired AccountIntegrityRepository integrity;

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Test
  void reportsUnboundedLedgerNetAgainstTheMaterializedSnapshot() {
    UUID userId = UUID.fromString("019b76da-a000-7000-8000-0000000001a4");
    jdbc.update(
        """
        INSERT INTO account(user_id, available_amount, available_currency,
          locked_amount, locked_currency, created_at, updated_at)
        VALUES (?, 10, 'KRW', 0, 'KRW', now(), now())
        """,
        userId);
    jdbc.update(
        """
        INSERT INTO ledger_entry(entry_id, account_id, bucket, side, amount, currency,
          reason, idempotency_key, operation_group_id, created_at)
        VALUES (?, ?, 'AVAILABLE', 'DEBIT', 10, 'KRW', 'DEPOSIT', ?, ?, now())
        """,
        UUID.fromString("019b76da-a000-7000-8000-0000000001a5"),
        userId,
        "integrity:account-net",
        UUID.fromString("019b76da-a000-7000-8000-0000000001a6"));

    assertThat(integrity.findSnapshotDrift()).isEmpty();

    jdbc.update("UPDATE account SET available_amount = 9 WHERE user_id = ?", userId);

    assertThat(integrity.findSnapshotDrift())
        .singleElement()
        .satisfies(
            drift -> {
              assertThat(drift.userId()).isEqualTo(userId);
              assertThat(drift.availableSnapshot()).isEqualTo(BigInteger.valueOf(9L));
              assertThat(drift.availableLedgerNet()).isEqualTo(BigInteger.TEN);
            });
  }

  @Test
  void reportsOnlyNonSystemLedgerAccountsWithoutSnapshots() {
    UUID orphan = UUID.fromString("019b76da-a000-7000-8000-0000000001a7");
    UUID house = UUID.fromString("00000000-0000-7000-8000-000000000001");
    UUID external = UUID.fromString("00000000-0000-7000-8000-000000000002");
    UUID systemGroup = UUID.randomUUID();
    UUID orphanGroup = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO ledger_entry(entry_id, account_id, bucket, side, amount, currency,
          reason, idempotency_key, operation_group_id, created_at) VALUES
        (?, ?, 'AVAILABLE', 'DEBIT', 10, 'KRW', 'BET_ADJUSTMENT', 'system:pair', ?, now()),
        (?, ?, 'AVAILABLE', 'CREDIT', 10, 'KRW', 'BET_ADJUSTMENT', 'system:pair', ?, now()),
        (?, ?, 'AVAILABLE', 'DEBIT', 10, 'KRW', 'BET_ADJUSTMENT', 'orphan:pair', ?, now()),
        (?, ?, 'AVAILABLE', 'CREDIT', 10, 'KRW', 'BET_ADJUSTMENT', 'orphan:pair', ?, now())
        """,
        UUID.randomUUID(),
        house,
        systemGroup,
        UUID.randomUUID(),
        external,
        systemGroup,
        UUID.randomUUID(),
        orphan,
        orphanGroup,
        UUID.randomUUID(),
        house,
        orphanGroup);

    assertThat(integrity.findOrphanLedgerAccountIds()).containsExactly(orphan);
  }

  @Test
  void sumsLedgerAmountsBeyondTheLongRange() {
    UUID userId = UUID.fromString("019b76da-a000-7000-8000-0000000001a7");
    jdbc.update(
        """
        INSERT INTO account(user_id, available_currency, locked_currency, created_at, updated_at)
        VALUES (?, 'KRW', 'KRW', now(), now())
        """,
        userId);
    insertDebit(userId, "1a8", "1a9", "integrity:bigint:first");
    insertDebit(userId, "1aa", "1ab", "integrity:bigint:second");

    assertThat(integrity.findSnapshotDrift())
        .singleElement()
        .extracting(AccountIntegrityRepository.AccountSnapshotDrift::availableLedgerNet)
        .isEqualTo(new BigInteger("18446744073709551614"));
  }

  private void insertDebit(UUID userId, String entryTail, String groupTail, String key) {
    jdbc.update(
        """
        INSERT INTO ledger_entry(entry_id, account_id, bucket, side, amount, currency,
          reason, idempotency_key, operation_group_id, created_at)
        VALUES (?, ?, 'AVAILABLE', 'DEBIT', 9223372036854775807, 'KRW',
          'DEPOSIT', ?, ?, now())
        """,
        UUID.fromString("019b76da-a000-7000-8000-000000000" + entryTail),
        userId,
        key,
        UUID.fromString("019b76da-a000-7000-8000-000000000" + groupTail));
  }
}
