package com.sportsbook.wallet.integrity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
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

  @Test
  void acceptsEverySupportedLedgerTopology() {
    UUID user = UUID.fromString("019b76da-a000-7000-8000-0000000001b0");
    UUID house = UUID.fromString("00000000-0000-7000-8000-000000000001");
    UUID external = UUID.fromString("00000000-0000-7000-8000-000000000002");
    List<Topology> cases =
        List.of(
            new Topology("PLATFORM", "DEPOSIT", user, "AVAILABLE", external, "AVAILABLE"),
            new Topology("PLATFORM", "WITHDRAW", external, "AVAILABLE", user, "AVAILABLE"),
            new Topology("BETTING", "BET_DEBIT", user, "LOCKED", user, "AVAILABLE"),
            new Topology("SETTLEMENT", "BET_PAYOUT", user, "AVAILABLE", house, "AVAILABLE"),
            new Topology("SETTLEMENT", "BET_REFUND", user, "AVAILABLE", user, "LOCKED"),
            new Topology("SETTLEMENT", "BET_REFUND", user, "AVAILABLE", house, "AVAILABLE"),
            new Topology("SETTLEMENT", "BET_FORFEIT", house, "AVAILABLE", user, "LOCKED"),
            new Topology("SETTLEMENT", "BET_ADJUSTMENT", user, "AVAILABLE", house, "AVAILABLE"),
            new Topology("SETTLEMENT", "BET_ADJUSTMENT", house, "AVAILABLE", user, "AVAILABLE"));

    for (int index = 0; index < cases.size(); index++) {
      Topology topology = cases.get(index);
      String key = "integrity:topology:" + index;
      UUID group = UUID.randomUUID();
      jdbc.update(
          """
          INSERT INTO wallet_operation(idempotency_key, caller_id, operation_kind, user_id,
            request_amount, request_currency, request_fingerprint, status, operation_group_id,
            requested_at, updated_at, completed_at)
          VALUES (?, ?, ?, ?, 10, 'KRW', ?, 'SUCCEEDED', ?, now(), now(), now())
          """,
          key,
          topology.caller(),
          topology.kind(),
          user,
          "b".repeat(64),
          group);
      insertLeg(
          UUID.randomUUID(),
          topology.debit(),
          topology.debitBucket(),
          "DEBIT",
          key,
          group,
          topology.kind());
      insertLeg(
          UUID.randomUUID(),
          topology.credit(),
          topology.creditBucket(),
          "CREDIT",
          key,
          group,
          topology.kind());
    }

    assertThat(integrity.findGroupDriftKeys()).isEmpty();
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

  private void insertLeg(
      UUID entryId,
      UUID accountId,
      String bucket,
      String side,
      String key,
      UUID groupId,
      String reason) {
    jdbc.update(
        """
        INSERT INTO ledger_entry(entry_id, account_id, bucket, side, amount, currency,
          reason, idempotency_key, operation_group_id, created_at)
        VALUES (?, ?, ?, ?, 10, 'KRW', ?, ?, ?, now())
        """,
        entryId,
        accountId,
        bucket,
        side,
        reason,
        key,
        groupId);
  }

  private record Topology(
      String caller,
      String kind,
      UUID debit,
      String debitBucket,
      UUID credit,
      String creditBucket) {}
}
