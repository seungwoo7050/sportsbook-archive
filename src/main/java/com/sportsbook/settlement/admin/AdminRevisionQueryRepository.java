package com.sportsbook.settlement.admin;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class AdminRevisionQueryRepository {

  private final JdbcTemplate jdbc;

  public AdminRevisionQueryRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional(readOnly = true)
  public Optional<View> find(UUID revisionId) {
    return jdbc
        .query(
            """
            select revision_id, bet_id, revision_number, event_id, source_candidate_id,
                state, attempt_count, next_retry_at, last_error_code, lease_until,
                wallet_status, wallet_queue_sequence, wallet_operation_group_id,
                wallet_queued_at, wallet_applied_at, wallet_next_attempt_at,
                created_at, updated_at, applied_at
            from settlement_revision where revision_id = ?
            """,
            (result, rowNumber) ->
                new View(
                    result.getObject("revision_id", UUID.class),
                    result.getObject("bet_id", UUID.class),
                    result.getLong("revision_number"),
                    result.getObject("event_id", UUID.class),
                    result.getObject("source_candidate_id", UUID.class),
                    result.getString("state"),
                    result.getInt("attempt_count"),
                    instant(result.getTimestamp("next_retry_at")),
                    result.getString("last_error_code"),
                    instant(result.getTimestamp("lease_until")),
                    result.getString("wallet_status"),
                    (Long) result.getObject("wallet_queue_sequence"),
                    result.getObject("wallet_operation_group_id", UUID.class),
                    instant(result.getTimestamp("wallet_queued_at")),
                    instant(result.getTimestamp("wallet_applied_at")),
                    instant(result.getTimestamp("wallet_next_attempt_at")),
                    result.getTimestamp("created_at").toInstant(),
                    result.getTimestamp("updated_at").toInstant(),
                    instant(result.getTimestamp("applied_at"))),
            revisionId)
        .stream()
        .findFirst();
  }

  private static Instant instant(Timestamp value) {
    return value == null ? null : value.toInstant();
  }

  public record View(
      UUID revisionId,
      UUID betId,
      long revisionNumber,
      UUID eventId,
      UUID sourceCandidateId,
      String state,
      int attemptCount,
      Instant nextRetryAt,
      String lastErrorCode,
      Instant leaseUntil,
      String walletStatus,
      Long walletQueueSequence,
      UUID walletOperationGroupId,
      Instant walletQueuedAt,
      Instant walletAppliedAt,
      Instant walletNextAttemptAt,
      Instant createdAt,
      Instant updatedAt,
      Instant appliedAt) {}
}
