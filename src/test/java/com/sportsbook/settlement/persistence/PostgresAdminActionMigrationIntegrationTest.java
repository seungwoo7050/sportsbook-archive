package com.sportsbook.settlement.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;

class PostgresAdminActionMigrationIntegrationTest extends PostgresIntegrationSupport {

  @Test
  void acceptsOnlyFinalAppendOnlyActionShapes() {
    UUID key = UUID.randomUUID();
    UUID target = UUID.randomUUID();
    String fingerprint = "a".repeat(64);

    assertThat(
            jdbc.update(
                """
                insert into settlement_admin_action (
                    idempotency_key, action_kind, target_id, request_fingerprint, outcome)
                values (?, 'CANDIDATE_APPROVE', ?, ?, 'CANDIDATE_APPROVED')
                """,
                key,
                target,
                fingerprint))
        .isOne();
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update settlement_admin_action set outcome='CANDIDATE_REJECTED' "
                        + "where idempotency_key=?",
                    key))
        .isInstanceOf(DataAccessException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    """
                    insert into settlement_admin_action (
                        idempotency_key, action_kind, target_id, request_fingerprint, outcome)
                    values (?, 'REVISION_RETRY', ?, ?, 'REVISION_RETRY_QUEUED')
                    """,
                    UUID.randomUUID(),
                    target,
                    fingerprint))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    """
                    insert into settlement_admin_action (
                        idempotency_key, action_kind, target_id, request_fingerprint, outcome)
                    values (?, 'CANDIDATE_REJECT', ?, 'not-a-digest', 'CANDIDATE_REJECTED')
                    """,
                    UUID.randomUUID(),
                    target))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
