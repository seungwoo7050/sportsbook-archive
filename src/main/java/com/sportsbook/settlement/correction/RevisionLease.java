package com.sportsbook.settlement.correction;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record RevisionLease(UUID token, Instant until) {

  public RevisionLease {
    Objects.requireNonNull(token, "token");
    Objects.requireNonNull(until, "until");
  }

  public static RevisionLease acquire(Instant now, Duration duration) {
    Objects.requireNonNull(now, "now");
    if (duration == null || duration.isZero() || duration.isNegative()) {
      throw new IllegalArgumentException("Revision lease duration must be positive");
    }
    return new RevisionLease(UUID.randomUUID(), now.plus(duration));
  }

  public boolean isExpiredAt(Instant instant) {
    return !until.isAfter(Objects.requireNonNull(instant, "instant"));
  }
}
