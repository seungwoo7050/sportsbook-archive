package com.sportsbook.settlement.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public final class DatabaseTimeSource {

  private final JdbcTemplate jdbc;

  public DatabaseTimeSource(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Instant currentTimestamp() {
    Timestamp timestamp = jdbc.queryForObject("select current_timestamp", Timestamp.class);
    return Objects.requireNonNull(timestamp, "Database timestamp").toInstant();
  }
}
