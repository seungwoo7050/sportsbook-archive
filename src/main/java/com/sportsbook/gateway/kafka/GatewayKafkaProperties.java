package com.sportsbook.gateway.kafka;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Bounded publication settings for failed raw event records. */
@ConfigurationProperties(prefix = "gateway.kafka")
public record GatewayKafkaProperties(
    @DefaultValue("11s") Duration dltWaitTimeout,
    @DefaultValue("1s") Duration dltTimeoutBuffer) {

  public GatewayKafkaProperties {
    requirePositive(dltWaitTimeout, "dlt-wait-timeout");
    requirePositive(dltTimeoutBuffer, "dlt-timeout-buffer");
  }

  private static void requirePositive(Duration value, String property) {
    if (value == null || value.isZero() || value.isNegative()) {
      throw new IllegalArgumentException("gateway.kafka." + property + " must be positive");
    }
  }
}
