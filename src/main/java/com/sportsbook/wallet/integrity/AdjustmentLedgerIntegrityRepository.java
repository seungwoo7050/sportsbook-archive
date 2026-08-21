package com.sportsbook.wallet.integrity;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Reconciles applied correction snapshots with their signed two-leg ledger transfer. */
@Repository
public class AdjustmentLedgerIntegrityRepository {
  private static final String LEDGER_DRIFT_SQL =
      """
      WITH applied AS (
        SELECT a.idempotency_key, a.delta_amount,
          COUNT(l.entry_id) AS entries,
          COUNT(l.entry_id) FILTER (WHERE l.side = 'DEBIT') AS debits,
          COUNT(l.entry_id) FILTER (WHERE l.side = 'CREDIT') AS credits,
          COALESCE(BOOL_AND(
            l.idempotency_key = a.idempotency_key
            AND l.operation_group_id = a.operation_group_id
            AND l.reason = 'BET_ADJUSTMENT'
            AND l.amount::NUMERIC = ABS(a.delta_amount::NUMERIC)
            AND l.currency = a.currency
            AND l.created_at = a.applied_at
          ), FALSE) AS matches_snapshot,
          CASE WHEN a.delta_amount > 0 THEN
            BOOL_OR(l.side = 'DEBIT' AND l.account_id = a.user_id
              AND l.bucket = 'AVAILABLE')
            AND BOOL_OR(l.side = 'CREDIT' AND l.account_id =
              '00000000-0000-7000-8000-000000000001' AND l.bucket = 'AVAILABLE')
          ELSE
            BOOL_OR(l.side = 'DEBIT' AND l.account_id =
              '00000000-0000-7000-8000-000000000001' AND l.bucket = 'AVAILABLE')
            AND BOOL_OR(l.side = 'CREDIT' AND l.account_id = a.user_id
              AND l.bucket = 'AVAILABLE')
          END AS matches_signed_topology
        FROM wallet_adjustment a
        LEFT JOIN ledger_entry l ON l.operation_group_id = a.operation_group_id
        WHERE a.status = 'APPLIED'
        GROUP BY a.revision_id
      )
      SELECT idempotency_key FROM applied
      WHERE entries <> 2 OR debits <> 1 OR credits <> 1
        OR NOT matches_snapshot OR NOT matches_signed_topology
      ORDER BY idempotency_key
      """;

  private final JdbcTemplate jdbc;

  public AdjustmentLedgerIntegrityRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<String> findLedgerDriftKeys() {
    return jdbc.queryForList(LEDGER_DRIFT_SQL, String.class);
  }
}
