package com.sportsbook.wallet.integrity;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Projects durable outcomes onto their exact two-row ledger groups. */
@Repository
public class OperationIntegrityRepository {
  private static final String GROUP_DRIFT_SQL =
      """
      WITH succeeded AS (
        SELECT o.idempotency_key, COUNT(l.entry_id) AS entries,
          COUNT(l.entry_id) FILTER (WHERE l.side = 'DEBIT') AS debits,
          COUNT(l.entry_id) FILTER (WHERE l.side = 'CREDIT') AS credits,
          COALESCE(BOOL_AND(
            l.idempotency_key = o.idempotency_key
            AND l.amount = o.request_amount
            AND l.currency = o.request_currency
            AND l.reason = o.operation_kind
            AND l.created_at = o.completed_at
          ), FALSE) AS matches_request,
          MIN(l.created_at) IS NOT DISTINCT FROM MAX(l.created_at) AS one_timestamp,
          CASE o.operation_kind
            WHEN 'DEPOSIT' THEN
              BOOL_OR(l.side = 'DEBIT' AND l.account_id = o.user_id AND l.bucket = 'AVAILABLE')
              AND BOOL_OR(l.side = 'CREDIT' AND l.account_id =
                '00000000-0000-7000-8000-000000000002' AND l.bucket = 'AVAILABLE')
            WHEN 'WITHDRAW' THEN
              BOOL_OR(l.side = 'DEBIT' AND l.account_id =
                '00000000-0000-7000-8000-000000000002' AND l.bucket = 'AVAILABLE')
              AND BOOL_OR(l.side = 'CREDIT' AND l.account_id = o.user_id AND l.bucket = 'AVAILABLE')
            WHEN 'BET_DEBIT' THEN
              BOOL_OR(l.side = 'DEBIT' AND l.account_id = o.user_id AND l.bucket = 'LOCKED')
              AND BOOL_OR(l.side = 'CREDIT' AND l.account_id = o.user_id AND l.bucket = 'AVAILABLE')
            WHEN 'BET_PAYOUT' THEN
              BOOL_OR(l.side = 'DEBIT' AND l.account_id = o.user_id AND l.bucket = 'AVAILABLE')
              AND BOOL_OR(l.side = 'CREDIT' AND l.account_id =
                '00000000-0000-7000-8000-000000000001' AND l.bucket = 'AVAILABLE')
            WHEN 'BET_REFUND' THEN
              BOOL_OR(l.side = 'DEBIT' AND l.account_id = o.user_id AND l.bucket = 'AVAILABLE')
              AND BOOL_OR(l.side = 'CREDIT' AND ((l.account_id = o.user_id AND l.bucket = 'LOCKED')
                OR (l.account_id = '00000000-0000-7000-8000-000000000001'
                  AND l.bucket = 'AVAILABLE')))
            WHEN 'BET_FORFEIT' THEN
              BOOL_OR(l.side = 'DEBIT' AND l.account_id =
                '00000000-0000-7000-8000-000000000001' AND l.bucket = 'AVAILABLE')
              AND BOOL_OR(l.side = 'CREDIT' AND l.account_id = o.user_id AND l.bucket = 'LOCKED')
            WHEN 'BET_ADJUSTMENT' THEN
              (BOOL_OR(l.side = 'DEBIT' AND l.account_id = o.user_id AND l.bucket = 'AVAILABLE')
                AND BOOL_OR(l.side = 'CREDIT' AND l.account_id =
                  '00000000-0000-7000-8000-000000000001' AND l.bucket = 'AVAILABLE'))
              OR (BOOL_OR(l.side = 'DEBIT' AND l.account_id =
                  '00000000-0000-7000-8000-000000000001' AND l.bucket = 'AVAILABLE')
                AND BOOL_OR(l.side = 'CREDIT' AND l.account_id = o.user_id
                  AND l.bucket = 'AVAILABLE'))
          END AS matches_topology
        FROM wallet_operation o
        LEFT JOIN ledger_entry l ON l.operation_group_id = o.operation_group_id
        WHERE o.status = 'SUCCEEDED'
        GROUP BY o.idempotency_key
      ), bad_succeeded AS (
        SELECT idempotency_key FROM succeeded
        WHERE entries <> 2 OR debits <> 1 OR credits <> 1
          OR NOT matches_request OR NOT one_timestamp OR NOT matches_topology
      ), orphan_ledger AS (
        SELECT DISTINCT l.idempotency_key
        FROM ledger_entry l
        LEFT JOIN wallet_operation o ON o.operation_group_id = l.operation_group_id
        WHERE o.operation_group_id IS NULL OR o.status <> 'SUCCEEDED'
      )
      SELECT idempotency_key FROM bad_succeeded
      UNION
      SELECT idempotency_key FROM orphan_ledger
      ORDER BY idempotency_key
      """;

  private final JdbcTemplate jdbc;

  public OperationIntegrityRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<String> findGroupDriftKeys() {
    return jdbc.queryForList(GROUP_DRIFT_SQL, String.class);
  }
}
