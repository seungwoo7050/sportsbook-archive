package com.sportsbook.gateway.kafka;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Bounded publication settings for failed raw event records. */
@ConfigurationProperties(prefix = "gateway.kafka")
public record GatewayKafkaProperties(
    @DefaultValue("1s") Duration retryInterval,
    @DefaultValue("2") long retryAttempts,
    @DefaultValue("11s") Duration dltWaitTimeout,
    @DefaultValue("1s") Duration dltTimeoutBuffer) {

  public GatewayKafkaProperties {
    requirePositive(retryInterval, "retry-interval");
    if (retryAttempts < 0) {
      throw new IllegalArgumentException("gateway.kafka.retry-attempts must not be negative");
    }
    requirePositive(dltWaitTimeout, "dlt-wait-timeout");
    requirePositive(dltTimeoutBuffer, "dlt-timeout-buffer");
  }

  private static void requirePositive(Duration value, String property) {
    if (value == null || value.isZero() || value.isNegative()) {
      throw new IllegalArgumentException("gateway.kafka." + property + " must be positive");
    }
  }
}
