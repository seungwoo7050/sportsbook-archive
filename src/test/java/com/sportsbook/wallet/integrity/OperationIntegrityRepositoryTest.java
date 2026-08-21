package com.sportsbook.wallet.integrity;

import static org.assertj.core.api.Assertions.assertThat;

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
@Import(OperationIntegrityRepository.class)
class OperationIntegrityRepositoryTest {
  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired JdbcTemplate jdbc;
  @Autowired OperationIntegrityRepository integrity;

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Test
  void detectsACompletedOperationWithAnIncompleteLedgerGroup() {
    UUID userId = UUID.fromString("019b76da-a000-7000-8000-0000000001ac");
    UUID groupId = UUID.fromString("019b76da-a000-7000-8000-0000000001ad");
    String key = "integrity:operation-group";
    jdbc.update(
        """
        INSERT INTO wallet_operation(idempotency_key, caller_id, operation_kind, user_id,
          request_amount, request_currency, request_fingerprint, status, operation_group_id,
          requested_at, updated_at, completed_at)
        VALUES (?, 'PLATFORM', 'DEPOSIT', ?, 10, 'KRW', ?, 'SUCCEEDED', ?,
          TIMESTAMPTZ '2026-01-01T00:00:00Z', TIMESTAMPTZ '2026-01-01T00:00:00Z',
          TIMESTAMPTZ '2026-01-01T00:00:00Z')
        """,
        key,
        userId,
        "a".repeat(64),
        groupId);
    insertLeg("1ae", userId, "DEBIT", key, groupId);
    insertLeg(
        "1af", UUID.fromString("00000000-0000-7000-8000-000000000002"), "CREDIT", key, groupId);

    assertThat(integrity.findGroupDriftKeys()).isEmpty();

    jdbc.update(
        "UPDATE ledger_entry SET bucket = 'LOCKED' WHERE operation_group_id = ? AND side = 'DEBIT'",
        groupId);
    assertThat(integrity.findGroupDriftKeys()).containsExactly(key);

    jdbc.update(
        "UPDATE ledger_entry SET bucket = 'AVAILABLE' WHERE operation_group_id = ?", groupId);
    jdbc.update(
        "DELETE FROM ledger_entry WHERE operation_group_id = ? AND side = 'CREDIT'", groupId);

    assertThat(integrity.findGroupDriftKeys()).containsExactly(key);
  }

  private void insertLeg(String tail, UUID accountId, String side, String key, UUID groupId) {
    jdbc.update(
        """
        INSERT INTO ledger_entry(entry_id, account_id, bucket, side, amount, currency,
          reason, idempotency_key, operation_group_id, created_at)
        VALUES (?, ?, 'AVAILABLE', ?, 10, 'KRW', 'DEPOSIT', ?, ?,
          TIMESTAMPTZ '2026-01-01T00:00:00Z')
        """,
        UUID.fromString("019b76da-a000-7000-8000-000000000" + tail),
        accountId,
        side,
        key,
        groupId);
  }
}
