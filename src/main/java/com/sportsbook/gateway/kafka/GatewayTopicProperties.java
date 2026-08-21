package com.sportsbook.gateway.kafka;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/** Names the four event streams consumed by the gateway. */
@ConfigurationProperties(prefix = "gateway.topics")
public record GatewayTopicProperties(
    String oddsChanged, String betSettled, String betVoided, String betResolutionRevised) {

  private static final int INPUT_TOPIC_COUNT = 4;
  private static final String DEAD_LETTER_SUFFIX = ".DLT";

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

  public TopicPartition deadLetterDestination(String sourceTopic, int sourcePartition) {
    String destination = sourceToDeadLetter().get(sourceTopic);
    if (destination == null) {
      throw new IllegalArgumentException("No gateway DLT is defined for topic " + sourceTopic);
    }
    return new TopicPartition(destination, sourcePartition);
  }

  public Map<String, String> sourceToDeadLetter() {
    Map<String, String> destinations = new LinkedHashMap<>();
    inputTopics().forEach(topic -> destinations.put(topic, topic + DEAD_LETTER_SUFFIX));
    return Map.copyOf(destinations);
  }

  private static Set<String> inputTopics(
      String odds, String settled, String voided, String revised) {
    return Set.of(odds, settled, voided, revised);
  }

  private static void requireTopic(String value, String property) {
    if (!StringUtils.hasText(value)) {
      throw new IllegalArgumentException("gateway.topics." + property + " must not be blank");
    }
    if (value.endsWith(DEAD_LETTER_SUFFIX)) {
      throw new IllegalArgumentException("gateway input topic must not itself be a DLT: " + value);
    }
  }
}
