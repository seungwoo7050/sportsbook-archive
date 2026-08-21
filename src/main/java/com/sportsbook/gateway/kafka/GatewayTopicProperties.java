package com.sportsbook.gateway.kafka;

import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/** Names the four event streams consumed by the gateway. */
@ConfigurationProperties(prefix = "gateway.topics")
public record GatewayTopicProperties(
    String oddsChanged, String betSettled, String betVoided, String betResolutionRevised) {

  private static final int INPUT_TOPIC_COUNT = 4;

  public GatewayTopicProperties {
    requireTopic(oddsChanged, "odds-changed");
    requireTopic(betSettled, "bet-settled");
    requireTopic(betVoided, "bet-voided");
    requireTopic(betResolutionRevised, "bet-resolution-revised");
    if (inputTopics(oddsChanged, betSettled, betVoided, betResolutionRevised).size()
        != INPUT_TOPIC_COUNT) {
      throw new IllegalArgumentException("gateway input topics must be distinct");
    }
  }

  public Set<String> inputTopics() {
    return inputTopics(oddsChanged, betSettled, betVoided, betResolutionRevised);
  }

  private static Set<String> inputTopics(
      String odds, String settled, String voided, String revised) {
    return Set.of(odds, settled, voided, revised);
  }

  private static void requireTopic(String value, String property) {
    if (!StringUtils.hasText(value)) {
      throw new IllegalArgumentException("gateway.topics." + property + " must not be blank");
    }
  }
}
