package com.sportsbook.wallet.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Exponential recovery retry delay with a bounded configured cap. */
@Component
public class RecoveryRetryPolicy {
  private static final int MAX_EXPONENT = 62;

  private final Duration baseDelay;
  private final Duration maximumDelay;

  public RecoveryRetryPolicy(
      @Value("${wallet.recovery.retry-base:PT1S}") Duration baseDelay,
      @Value("${wallet.recovery.retry-cap:PT60S}") Duration maximumDelay) {
    this.baseDelay = positive(baseDelay, "baseDelay");
    this.maximumDelay = positive(maximumDelay, "maximumDelay");
    if (baseDelay.compareTo(maximumDelay) > 0) {
      throw new IllegalArgumentException("baseDelay must not exceed maximumDelay");
    }
  }

  public Instant retryAt(Instant attemptedAt, int completedRetries) {
    Objects.requireNonNull(attemptedAt, "attemptedAt");
    if (completedRetries < 0) {
      throw new IllegalArgumentException("completedRetries must be nonnegative");
    }
    long multiplier = 1L << Math.min(completedRetries, MAX_EXPONENT);
    Duration delay;
    try {
      Duration candidate = baseDelay.multipliedBy(multiplier);
      delay = candidate.compareTo(maximumDelay) < 0 ? candidate : maximumDelay;
    } catch (ArithmeticException overflow) {
      delay = maximumDelay;
    }
    return attemptedAt.plus(delay);
  }

  private static Duration positive(Duration duration, String name) {
    Objects.requireNonNull(duration, name);
    if (duration.toMillis() < 1L) {
      throw new IllegalArgumentException(name + " must be positive");
    }
    return duration;
  }
}
