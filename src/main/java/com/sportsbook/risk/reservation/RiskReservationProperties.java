package com.sportsbook.risk.reservation;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Lease and tombstone lifetimes for idempotent reservation state. */
@ConfigurationProperties(prefix = "risk.reservations")
public record RiskReservationProperties(Duration lease, Duration retention) {
  public static final Duration DEFAULT_LEASE = Duration.ofMinutes(2);
  public static final Duration DEFAULT_RETENTION = Duration.ofDays(32);
  private static final Duration LONGEST_COUNTER_WINDOW = Duration.ofDays(30);

  public RiskReservationProperties {
    lease = lease == null ? DEFAULT_LEASE : lease;
    retention = retention == null ? DEFAULT_RETENTION : retention;
    if (lease.isZero() || lease.isNegative()) {
      throw new IllegalArgumentException("reservation lease must be positive");
    }
    if (retention.compareTo(lease) <= 0) {
      throw new IllegalArgumentException("reservation retention must exceed the lease");
    }
    if (retention.compareTo(LONGEST_COUNTER_WINDOW) <= 0) {
      throw new IllegalArgumentException("reservation retention must exceed the monthly window");
    }
  }
}
