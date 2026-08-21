package com.sportsbook.risk.event;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Kafka topic ownership for accepted bets, quarantine, and risk signals. */
@ConfigurationProperties(prefix = "risk.topics")
public record EventTopics(
    String betPlaced, String betPlacedDlt, String limitViolated, String patternSuspected) {
  public EventTopics {
    betPlaced = valueOrDefault(betPlaced, "bet.placed.v1");
    betPlacedDlt = valueOrDefault(betPlacedDlt, "bet.placed.v1.DLT");
    limitViolated = valueOrDefault(limitViolated, "risk.limit.violated");
    patternSuspected = valueOrDefault(patternSuspected, "risk.pattern.suspected");
  }

  private static String valueOrDefault(String value, String fallback) {
    if (value == null) {
      return fallback;
    }
    if (value.isBlank()) {
      throw new IllegalArgumentException("topic names must not be blank");
    }
    return value;
  }
}
