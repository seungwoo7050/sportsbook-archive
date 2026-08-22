package com.sportsbook.settlement.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("settlement.runtime")
public record SettlementRuntimeProperties(
    Duration correctionWindow, Duration recoveryInterval, Duration leaseDuration, int batchSize) {

  public SettlementRuntimeProperties {
    correctionWindow = defaulted(correctionWindow, Duration.ofHours(24));
    recoveryInterval = defaulted(recoveryInterval, Duration.ofSeconds(1));
    leaseDuration = defaulted(leaseDuration, Duration.ofSeconds(30));
    batchSize = batchSize == 0 ? 100 : batchSize;
    if (correctionWindow.isNegative()
        || recoveryInterval.isZero()
        || recoveryInterval.isNegative()
        || leaseDuration.isZero()
        || leaseDuration.isNegative()
        || batchSize < 1
        || batchSize > 1000) {
      throw new IllegalArgumentException("Invalid settlement runtime bounds");
    }
  }

  private static Duration defaulted(Duration candidate, Duration fallback) {
    return candidate == null ? fallback : candidate;
  }
}
