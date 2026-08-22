package com.sportsbook.settlement.config;

import java.util.HashSet;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("settlement.topics")
public record SettlementTopics(
    String betPlaced,
    String matchResult,
    String eventLifecycle,
    String betSettled,
    String betVoided,
    String betResolutionRevised) {

  public SettlementTopics {
    betPlaced = defaulted(betPlaced, "bet.placed.v1");
    matchResult = defaulted(matchResult, "match.result");
    eventLifecycle = defaulted(eventLifecycle, "event.lifecycle");
    betSettled = defaulted(betSettled, "bet.settled.v1");
    betVoided = defaulted(betVoided, "bet.voided.v1");
    betResolutionRevised = defaulted(betResolutionRevised, "bet.resolution.revised.v1");
    List<String> topics =
        List.of(
            betPlaced,
            matchResult,
            eventLifecycle,
            betSettled,
            betVoided,
            betResolutionRevised);
    if (new HashSet<>(topics).size() != topics.size()) {
      throw new IllegalArgumentException("Settlement topics must be distinct");
    }
  }

  public String deadLetter(String sourceTopic) {
    return sourceTopic + ".DLT";
  }

  private static String defaulted(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }
}
