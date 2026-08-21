package com.sportsbook.wallet.persistence;

import com.sportsbook.protocol.value.IdempotencyKey;
import java.util.Objects;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Serializes first writers for one full idempotency key until their transaction completes. */
@Component
public class IdempotencyKeyLock {

  private static final String LOCK_NAMESPACE = "wallet:idempotency:";
  private static final String LOCK_SQL = "select pg_advisory_xact_lock(hashtextextended(?, 0))";

  private final JdbcTemplate jdbc;

  public IdempotencyKeyLock(JdbcTemplate jdbc) {
    this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
  }

  public void acquire(IdempotencyKey key) {
    Objects.requireNonNull(key, "key");
    if (!TransactionSynchronizationManager.isActualTransactionActive()) {
      throw new IllegalStateException("Idempotency advisory lock requires an active transaction");
    }
    try {
      jdbc.query(
          LOCK_SQL,
          statement -> statement.setString(1, LOCK_NAMESPACE + key.value()),
          resultSet -> null);
    } catch (DataAccessException failure) {
      throw PostgresFailureTranslator.translate(key, failure);
    }
  }
}
