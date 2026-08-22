package com.sportsbook.settlement.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;

public final class JdbcTimestamps {

  private JdbcTimestamps() {}

  public static Timestamp required(Instant value) {
    return Timestamp.from(Objects.requireNonNull(value, "value"));
  }

  public static Timestamp nullable(Instant value) {
    return value == null ? null : Timestamp.from(value);
  }
}
