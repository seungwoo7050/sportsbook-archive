package com.sportsbook.wallet.integrity;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Reconciles every adjustment proof with its durable operation outcome. */
@Repository
public class AdjustmentOperationIntegrityRepository {
  private static final String OUTCOME_DRIFT_SQL =
      """
      WITH adjustment_operations AS (
        SELECT * FROM wallet_operation WHERE operation_kind = 'BET_ADJUSTMENT'
      ), paired AS (
        SELECT COALESCE(a.idempotency_key, o.idempotency_key) AS outcome_key,
          a.idempotency_key AS proof_key, o.idempotency_key AS operation_key,
          a.status AS proof_status, o.status AS operation_status,
          a.operation_group_id AS proof_group, o.operation_group_id AS operation_group,
          a.applied_at, o.completed_at, a.created_at, o.requested_at,
          o.failure_code, o.failure_http_status, o.failure_title, o.failure_detail,
          o.failure_balance_amount, o.failure_balance_currency, o.failure_expected_currency,
          (SELECT COUNT(*) FROM ledger_entry l
            WHERE l.idempotency_key = COALESCE(a.idempotency_key, o.idempotency_key))
            AS ledger_entries,
          a.user_id AS proof_user, o.user_id AS operation_user,
          a.delta_amount, o.request_amount,
          a.currency AS proof_currency, o.request_currency AS operation_currency,
          o.caller_id
        FROM wallet_adjustment a
        FULL OUTER JOIN adjustment_operations o ON o.idempotency_key = a.idempotency_key
      )
      SELECT outcome_key FROM paired
      WHERE proof_key IS NULL OR operation_key IS NULL
        OR caller_id <> 'SETTLEMENT'
        OR proof_user <> operation_user
        OR ABS(delta_amount::NUMERIC) <> request_amount::NUMERIC
        OR proof_currency <> operation_currency
        OR created_at IS DISTINCT FROM requested_at
        OR NOT (
          (proof_status = 'REJECTED' AND operation_status = 'REJECTED'
            AND proof_group IS NULL AND operation_group IS NULL AND ledger_entries = 0
            AND failure_code IS NOT NULL AND failure_http_status BETWEEN 400 AND 499
            AND failure_title IS NOT NULL AND failure_detail IS NOT NULL
            AND created_at = completed_at)
          OR (proof_status = 'BLOCKED' AND operation_status = 'BLOCKED_FUNDS'
            AND proof_group IS NULL AND operation_group IS NULL AND ledger_entries = 0
            AND completed_at IS NULL
            AND failure_balance_amount IS NULL AND failure_balance_currency IS NULL
            AND failure_expected_currency IS NULL)
          OR (proof_status = 'APPLIED' AND operation_status = 'SUCCEEDED'
            AND proof_group IS NOT NULL AND proof_group = operation_group
            AND applied_at = completed_at
            AND failure_balance_amount IS NULL AND failure_balance_currency IS NULL
            AND failure_expected_currency IS NULL)
        )
      ORDER BY outcome_key
      """;

  private final JdbcTemplate jdbc;

  public AdjustmentOperationIntegrityRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<String> findOutcomeDriftKeys() {
    return jdbc.queryForList(OUTCOME_DRIFT_SQL, String.class);
  }
}
