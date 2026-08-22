package com.sportsbook.settlement.execution;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record SettlementLease(UUID token, Instant until) {

  public SettlementLease {
    Objects.requireNonNull(token, "token");
    Objects.requireNonNull(until, "until");
  }

  public static SettlementLease acquire(Instant now, Duration duration) {
    Objects.requireNonNull(now, "now");
    if (duration == null || duration.isZero() || duration.isNegative()) {
      throw new IllegalArgumentException("Lease duration must be positive");
    }
    return new SettlementLease(UUID.randomUUID(), now.plus(duration));
  }

  public boolean isExpiredAt(Instant instant) {
    return !until.isAfter(Objects.requireNonNull(instant, "instant"));
  }
}
