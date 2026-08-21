package com.sportsbook.wallet.integrity;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Reconciles recovery debt, freeze state, and the durable FIFO sequence. */
@Repository
public class RecoveryQueueIntegrityRepository {
  private static final String QUEUE_DRIFT_SQL =
      """
      WITH queue_summary AS (
        SELECT user_id,
          COUNT(queue_sequence) AS queued_count,
          MAX(queue_sequence) AS max_sequence,
          COUNT(*) FILTER (WHERE status = 'BLOCKED') AS blocked_count,
          COALESCE(SUM((-delta_amount)::NUMERIC) FILTER (WHERE status = 'BLOCKED'), 0)
            AS blocked_debt
        FROM wallet_adjustment
        WHERE queue_sequence IS NOT NULL
        GROUP BY user_id
      ), identities AS (
        SELECT user_id FROM account
        UNION
        SELECT user_id FROM queue_summary
      )
      SELECT i.user_id
      FROM identities i
      LEFT JOIN account a ON a.user_id = i.user_id
      LEFT JOIN queue_summary q ON q.user_id = i.user_id
      WHERE a.user_id IS NULL
        OR a.recovery_debt_amount <> COALESCE(q.blocked_debt, 0)
        OR (a.recovery_debt_amount > 0) <> (a.recovery_frozen_at IS NOT NULL)
        OR (a.recovery_debt_amount > 0) <> (COALESCE(q.blocked_count, 0) > 0)
        OR a.next_adjustment_sequence::NUMERIC <>
          COALESCE(q.max_sequence::NUMERIC + 1, 1)
        OR COALESCE(q.queued_count::NUMERIC, 0) <> COALESCE(q.max_sequence::NUMERIC, 0)
        OR EXISTS (
          SELECT 1
          FROM wallet_adjustment applied
          JOIN wallet_adjustment blocked ON blocked.user_id = applied.user_id
            AND blocked.status = 'BLOCKED'
            AND blocked.queue_sequence < applied.queue_sequence
          WHERE applied.user_id = i.user_id
            AND applied.status = 'APPLIED' AND applied.queue_sequence IS NOT NULL
        )
      ORDER BY i.user_id
      """;

  private final JdbcTemplate jdbc;

  public RecoveryQueueIntegrityRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<UUID> findQueueDriftUsers() {
    return jdbc.queryForList(QUEUE_DRIFT_SQL, UUID.class);
  }
}
