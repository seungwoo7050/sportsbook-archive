package com.sportsbook.wallet.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest(properties = "spring.test.database.replace=NONE")
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AdjustmentMigrationTest {
  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired JdbcTemplate jdbc;
  @Autowired PlatformTransactionManager transactions;

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Test
  void definesDurableProofAndFifoConstraints() {
    Map<String, String> constraints =
        jdbc
            .query(
                """
                SELECT conname, pg_get_constraintdef(oid)
                FROM pg_constraint
                WHERE conrelid = 'wallet_adjustment'::regclass
                """,
                (result, row) -> Map.entry(result.getString(1), result.getString(2)))
            .stream()
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    assertThat(constraints.get("uq_wallet_adjustment_bet_revision"))
        .contains("UNIQUE (bet_id, revision_number)");
    assertThat(constraints.get("uq_wallet_adjustment_user_sequence"))
        .contains("UNIQUE (user_id, queue_sequence)");
    assertThat(constraints.get("fk_wallet_adjustment_operation"))
        .contains("DEFERRABLE INITIALLY DEFERRED");
    assertThat(constraints.get("fk_wallet_adjustment_operation_group"))
        .contains("FOREIGN KEY (idempotency_key, operation_group_id)")
        .contains("DEFERRABLE INITIALLY DEFERRED");
    assertThat(constraints.keySet()).noneMatch(name -> name.contains("account"));
    assertThat(constraints.get("ck_wallet_adjustment_request"))
        .contains("revision_number >= 1")
        .contains("settlement:revision:")
        .contains("00000000-0000-7000-8000-000000000001")
        .contains("delta_amount = (new_payout_amount - previous_payout_amount)");
    assertThat(constraints.get("ck_wallet_adjustment_outcome"))
        .contains("delta_amount < 0")
        .contains("retry_count = 0");
    assertThat(constraints).doesNotContainKey("ck_wallet_adjustment_timestamps");
    assertThat(index("ix_wallet_adjustment_fifo"))
        .contains("user_id, queue_sequence")
        .contains("status", "'BLOCKED'");
    assertThat(index("ix_wallet_adjustment_due"))
        .contains("next_attempt_at, user_id")
        .contains("status", "'BLOCKED'");
  }

  @Test
  void permitsObservationalAdjustmentTimestamps() {
    jdbc.update(
        """
        INSERT INTO wallet_operation (
          idempotency_key, caller_id, operation_kind, user_id, request_amount, request_currency,
          request_fingerprint, status, requested_at, updated_at
        ) VALUES ('settlement:revision:019b76da-a000-7000-8000-000000000040',
          'SETTLEMENT', 'BET_ADJUSTMENT', '019b76da-a000-7000-8000-000000000042',
          5, 'KRW', repeat('a', 64), 'BLOCKED_FUNDS', now(), now())
        """);
    jdbc.update(
        """
        INSERT INTO wallet_adjustment (
          revision_id, idempotency_key, bet_id, revision_number, user_id,
          previous_payout_amount, new_payout_amount, delta_amount, currency, status,
          queue_sequence, queued_at, next_attempt_at, created_at, updated_at
        ) VALUES ('019b76da-a000-7000-8000-000000000040',
          'settlement:revision:019b76da-a000-7000-8000-000000000040',
          '019b76da-a000-7000-8000-000000000041', 1,
          '019b76da-a000-7000-8000-000000000042', 10, 5, -5, 'KRW', 'BLOCKED', 1,
          TIMESTAMPTZ '2025-01-02 00:00:00Z', TIMESTAMPTZ '2025-01-01 00:00:00Z',
          TIMESTAMPTZ '2999-01-01 00:00:00Z', TIMESTAMPTZ '2025-01-01 00:00:00Z')
        """);
    assertThat(observations("next_attempt_at < queued_at AND updated_at < created_at")).isTrue();

    jdbc.update(
        """
        UPDATE wallet_operation SET status='SUCCEEDED',
          operation_group_id='019b76da-a000-7000-8000-000000000043',
          completed_at=TIMESTAMPTZ '2024-01-01 00:00:00Z' WHERE idempotency_key=
          'settlement:revision:019b76da-a000-7000-8000-000000000040'
        """);
    jdbc.update(
        """
        UPDATE wallet_adjustment SET status='APPLIED', next_attempt_at=NULL,
          operation_group_id='019b76da-a000-7000-8000-000000000043',
          applied_at=TIMESTAMPTZ '2024-01-01 00:00:00Z',
          updated_at=TIMESTAMPTZ '2023-01-01 00:00:00Z'
        WHERE revision_id='019b76da-a000-7000-8000-000000000040'
        """);

    assertThat(observations("applied_at < queued_at AND updated_at < created_at")).isTrue();
  }

  private Boolean observations(String predicate) {
    return jdbc.queryForObject(
        "SELECT "
            + predicate
            + " FROM wallet_adjustment WHERE revision_id="
            + "'019b76da-a000-7000-8000-000000000040'",
        Boolean.class);
  }

  @Test
  void commitsAChildFirstProofThroughDeferredOperationKeys() {
    UUIDs ids = UUIDs.create("000000000201", "000000000202", "000000000203", "000000000204");
    String key = "settlement:revision:" + ids.revisionId();

    new TransactionTemplate(transactions)
        .executeWithoutResult(
            ignored -> {
              insertAppliedProof(ids, key, ids.groupId());
              insertSucceededOperation(ids, key, ids.groupId());
            });

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM wallet_adjustment WHERE revision_id=?",
                Integer.class,
                ids.revisionId()))
        .isOne();
  }

  @Test
  void rejectsAnAppliedProofWhoseOperationGroupDoesNotMatch() {
    UUIDs ids = UUIDs.create("000000000211", "000000000212", "000000000213", "000000000214");
    String key = "settlement:revision:" + ids.revisionId();

    assertThatThrownBy(
            () ->
                new TransactionTemplate(transactions)
                    .executeWithoutResult(
                        ignored -> {
                          insertAppliedProof(ids, key, ids.groupId());
                          insertSucceededOperation(ids, key, java.util.UUID.randomUUID());
                        }))
        .hasRootCauseInstanceOf(java.sql.SQLException.class);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM wallet_adjustment WHERE revision_id=?",
                Integer.class,
                ids.revisionId()))
        .isZero();
  }

  @Test
  void rejectsNoncanonicalKeysAndReservedUsers() {
    UUIDs ids = UUIDs.create("000000000221", "000000000222", "000000000223", "000000000224");
    assertRequestConstraint(ids, "settlement:revision:wrong");

    UUIDs house =
        new UUIDs(
            java.util.UUID.fromString("019b76da-a000-7000-8000-000000000225"),
            java.util.UUID.fromString("019b76da-a000-7000-8000-000000000226"),
            com.sportsbook.wallet.domain.SystemAccountIds.HOUSE,
            java.util.UUID.fromString("019b76da-a000-7000-8000-000000000227"));
    assertRequestConstraint(house, "settlement:revision:" + house.revisionId());
    UUIDs external =
        new UUIDs(
            java.util.UUID.fromString("019b76da-a000-7000-8000-000000000228"),
            java.util.UUID.fromString("019b76da-a000-7000-8000-000000000229"),
            com.sportsbook.wallet.domain.SystemAccountIds.EXTERNAL_PAYMENT,
            java.util.UUID.fromString("019b76da-a000-7000-8000-000000000230"));
    assertRequestConstraint(external, "settlement:revision:" + external.revisionId());
  }

  private void assertRequestConstraint(UUIDs ids, String key) {
    Throwable failure =
        catchThrowable(
            () ->
                new TransactionTemplate(transactions)
                    .executeWithoutResult(
                        ignored -> {
                          insertSucceededOperation(ids, key, ids.groupId());
                          insertAppliedProof(ids, key, ids.groupId());
                        }));
    assertThat(failure).rootCause().hasMessageContaining("ck_wallet_adjustment_request");
  }
  private void insertAppliedProof(UUIDs ids, String key, java.util.UUID groupId) {
    jdbc.update(
        """
        INSERT INTO wallet_adjustment (
            revision_id, idempotency_key, bet_id, revision_number, user_id,
            previous_payout_amount, new_payout_amount, delta_amount, currency,
            status, operation_group_id, applied_at, created_at, updated_at
        ) VALUES (?, ?, ?, 1, ?, 5, 10, 5, 'KRW', 'APPLIED', ?, now(), now(), now())
        """,
        ids.revisionId(),
        key,
        ids.betId(),
        ids.userId(),
        groupId);
  }

  private void insertSucceededOperation(UUIDs ids, String key, java.util.UUID groupId) {
    jdbc.update(
        """
        INSERT INTO wallet_operation (
            idempotency_key, caller_id, operation_kind, user_id, request_amount,
            request_currency, request_fingerprint, status, operation_group_id,
            requested_at, updated_at, completed_at
        ) VALUES (?, 'SETTLEMENT', 'BET_ADJUSTMENT', ?, 5, 'KRW', ?,
                  'SUCCEEDED', ?, now(), now(), now())
        """,
        key,
        ids.userId(),
        "a".repeat(64),
        groupId);
  }

  private record UUIDs(
      java.util.UUID revisionId,
      java.util.UUID betId,
      java.util.UUID userId,
      java.util.UUID groupId) {
    private static UUIDs create(String revision, String bet, String user, String group) {
      String prefix = "019b76da-a000-7000-8000-";
      return new UUIDs(
          java.util.UUID.fromString(prefix + revision),
          java.util.UUID.fromString(prefix + bet),
          java.util.UUID.fromString(prefix + user),
          java.util.UUID.fromString(prefix + group));
    }
  }

  private String index(String name) {
    return jdbc.queryForObject(
        "SELECT indexdef FROM pg_indexes WHERE indexname = ?", String.class, name);
  }
}
