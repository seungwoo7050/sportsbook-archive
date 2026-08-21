package com.sportsbook.wallet.integrity;

import com.sportsbook.wallet.domain.SystemAccountIds;
import java.math.BigInteger;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Reconciles materialized account buckets against unbounded numeric ledger nets. */
@Repository
public class AccountIntegrityRepository {
  private static final String SNAPSHOT_DRIFT_SQL =
      """
      WITH ledger_net AS (
        SELECT a.user_id,
          COALESCE(SUM(CASE WHEN l.bucket = 'AVAILABLE'
            THEN CASE l.side WHEN 'DEBIT' THEN l.amount ELSE -l.amount END
            ELSE 0 END), 0)::NUMERIC AS available_net,
          COALESCE(SUM(CASE WHEN l.bucket = 'LOCKED'
            THEN CASE l.side WHEN 'DEBIT' THEN l.amount ELSE -l.amount END
            ELSE 0 END), 0)::NUMERIC AS locked_net,
          COALESCE(BOOL_OR(l.currency <> a.available_currency), FALSE) AS currency_drift
        FROM account a
        LEFT JOIN ledger_entry l ON l.account_id = a.user_id
        GROUP BY a.user_id
      )
      SELECT a.user_id, a.available_amount, n.available_net,
        a.locked_amount, n.locked_net, a.available_currency
      FROM account a
      JOIN ledger_net n ON n.user_id = a.user_id
      WHERE a.available_amount::NUMERIC <> n.available_net
        OR a.locked_amount::NUMERIC <> n.locked_net
        OR n.currency_drift
      ORDER BY a.user_id
      """;
  private static final String ORPHAN_LEDGER_SQL =
      """
      SELECT DISTINCT l.account_id
      FROM ledger_entry l
      LEFT JOIN account a ON a.user_id = l.account_id
      WHERE a.user_id IS NULL AND l.account_id NOT IN (?, ?)
      ORDER BY l.account_id
      """;

  private final JdbcTemplate jdbc;

  public AccountIntegrityRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<AccountSnapshotDrift> findSnapshotDrift() {
    return jdbc.query(
        SNAPSHOT_DRIFT_SQL,
        (row, number) ->
            new AccountSnapshotDrift(
                row.getObject("user_id", UUID.class),
                BigInteger.valueOf(row.getLong("available_amount")),
                row.getBigDecimal("available_net").toBigIntegerExact(),
                BigInteger.valueOf(row.getLong("locked_amount")),
                row.getBigDecimal("locked_net").toBigIntegerExact(),
                row.getString("available_currency")));
  }

  public List<UUID> findOrphanLedgerAccountIds() {
    return jdbc.queryForList(
        ORPHAN_LEDGER_SQL, UUID.class, SystemAccountIds.HOUSE, SystemAccountIds.EXTERNAL_PAYMENT);
  }

  public record AccountSnapshotDrift(
      UUID userId,
      BigInteger availableSnapshot,
      BigInteger availableLedgerNet,
      BigInteger lockedSnapshot,
      BigInteger lockedLedgerNet,
      String currency) {}
}
