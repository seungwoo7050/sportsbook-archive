package com.sportsbook.wallet.integrity;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Reconciles rejected adjustments with the exact failure shape they can produce. */
@Repository
public class AdjustmentFailureIntegrityRepository {
  private static final String FAILURE_DRIFT_SQL =
      """
      SELECT a.idempotency_key
      FROM wallet_adjustment a
      JOIN wallet_operation o ON o.idempotency_key = a.idempotency_key
      WHERE a.status = 'REJECTED' AND (
        o.failure_detail IS NOT NULL AND (
          (o.failure_code = 'ACCOUNT_NOT_FOUND'
            AND o.failure_http_status = 404 AND o.failure_title = 'Account not found'
            AND o.failure_balance_amount IS NULL AND o.failure_balance_currency IS NULL
            AND o.failure_expected_currency IS NULL)
          OR (o.failure_code = 'CURRENCY_MISMATCH'
            AND o.failure_http_status = 422 AND o.failure_title = 'Currency mismatch'
            AND o.failure_balance_amount IS NULL AND o.failure_balance_currency IS NULL
            AND o.failure_expected_currency IN ('KRW', 'USD')
            AND o.failure_expected_currency <> o.request_currency)
          OR (o.failure_code = 'AMOUNT_OUT_OF_RANGE' AND a.delta_amount > 0
            AND o.failure_http_status = 422 AND o.failure_title = 'Amount out of range'
            AND o.failure_balance_amount IS NOT NULL AND o.failure_balance_amount >= 0
            AND o.failure_balance_currency = o.request_currency
            AND o.failure_expected_currency IS NULL)
        )
      ) IS NOT TRUE
      ORDER BY a.idempotency_key
      """;

  private final JdbcTemplate jdbc;

  public AdjustmentFailureIntegrityRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<String> findFailureDriftKeys() {
    return jdbc.queryForList(FAILURE_DRIFT_SQL, String.class);
  }
}
