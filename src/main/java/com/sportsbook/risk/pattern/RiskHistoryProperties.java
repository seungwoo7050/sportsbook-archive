package com.sportsbook.risk.pattern;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Bounds retained confirmed pattern facts for active and idle users. */
@ConfigurationProperties(prefix = "risk.history")
public record RiskHistoryProperties(Duration idleRetention, int maxStakeSamples) {
  private static final Duration DEFAULT_IDLE_RETENTION = Duration.ofDays(7);
  private static final int DEFAULT_MAX_STAKE_SAMPLES = 100;

  public RiskHistoryProperties {
    idleRetention = idleRetention == null ? DEFAULT_IDLE_RETENTION : idleRetention;
    maxStakeSamples = maxStakeSamples == 0 ? DEFAULT_MAX_STAKE_SAMPLES : maxStakeSamples;
    if (idleRetention.isZero() || idleRetention.isNegative()) {
      throw new IllegalArgumentException("history idle retention must be positive");
    }
    if (maxStakeSamples <= 0) {
      throw new IllegalArgumentException("history stake sample bound must be positive");
    }
  }
}
