package com.sportsbook.wallet.persistence;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Reads the authoritative PostgreSQL wall clock once after wallet locks are held. */
@Component
public class DatabaseClock {
  private final JdbcTemplate jdbc;

  public DatabaseClock(JdbcTemplate jdbc) {
    this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
  }

  public Instant now() {
    if (!TransactionSynchronizationManager.isActualTransactionActive()) {
      throw new IllegalStateException("Database clock requires an active transaction");
    }
    return jdbc.queryForObject(
        "SELECT clock_timestamp()",
        (result, row) -> result.getObject(1, OffsetDateTime.class).toInstant());
  }
}
