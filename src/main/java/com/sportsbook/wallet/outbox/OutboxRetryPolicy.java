package com.sportsbook.wallet.outbox;

import java.time.Duration;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OutboxRetryPolicy {

  private static final int MAX_ERROR_LENGTH = 1024;
  private static final int MAX_EXPONENT = 62;

  private final Duration baseDelay;
  private final Duration maximumDelay;

  public OutboxRetryPolicy(
      @Value("${wallet.outbox.retry-base:PT1S}") Duration baseDelay,
      @Value("${wallet.outbox.retry-cap:PT60S}") Duration maximumDelay) {
    this.baseDelay = positive(baseDelay, "baseDelay");
    this.maximumDelay = positive(maximumDelay, "maximumDelay");
    if (baseDelay.compareTo(maximumDelay) > 0) {
      throw new IllegalArgumentException("baseDelay must not exceed maximumDelay");
    }
  }

  public Duration delayForAttempt(int attemptCount) {
    if (attemptCount < 1) {
      throw new IllegalArgumentException("attemptCount must be positive");
    }
    long multiplier = 1L << Math.min(attemptCount - 1, MAX_EXPONENT);
    try {
      Duration delay = baseDelay.multipliedBy(multiplier);
      return delay.compareTo(maximumDelay) < 0 ? delay : maximumDelay;
    } catch (ArithmeticException overflow) {
      return maximumDelay;
    }
  }

  public String describe(Throwable failure) {
    Objects.requireNonNull(failure, "failure");
    String message = failure.getMessage();
    String description =
        failure.getClass().getSimpleName()
            + (message == null ? "" : ": " + message.replaceAll("[\\r\\n]+", " "));
    return description.substring(0, Math.min(description.length(), MAX_ERROR_LENGTH));
  }

  private static Duration positive(Duration duration, String name) {
    Objects.requireNonNull(duration, name);
    if (duration.toMillis() < 1L) {
      throw new IllegalArgumentException(name + " must be positive");
    }
    return duration;
  }
}
