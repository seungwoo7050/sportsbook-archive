package com.sportsbook.wallet.persistence;

import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Serializes one bet revision pair in a namespace disjoint from idempotency-key locks. */
@Component
public class AdjustmentPairLock {
  static final int NAMESPACE = 0x57414C4C;
  private static final String LOCK_SQL = "SELECT pg_advisory_xact_lock(?, hashtext(?))";

  private final JdbcTemplate jdbc;

  public AdjustmentPairLock(JdbcTemplate jdbc) {
    this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
  }

  public void acquire(UUID betId, long revisionNumber) {
    if (!TransactionSynchronizationManager.isActualTransactionActive()) {
      throw new IllegalStateException("Adjustment pair lock requires an active transaction");
    }
    String pair = Objects.requireNonNull(betId, "betId") + ":" + revisionNumber;
    jdbc.query(
        LOCK_SQL,
        statement -> {
          statement.setInt(1, NAMESPACE);
          statement.setString(2, pair);
        },
        resultSet -> null);
  }
}
