package com.sportsbook.gateway.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class GatewayTopicPropertiesTest {

  private final GatewayTopicProperties topics =
      new GatewayTopicProperties(
          "odds.changed", "bet.settled.v1", "bet.voided.v1", "bet.resolution.revised.v1");

  @Test
  void mapsOnlyTheFourExactUppercaseDeadLetterTopics() {
    assertThat(topics.sourceToDeadLetter())
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                "odds.changed", "odds.changed.DLT",
                "bet.settled.v1", "bet.settled.v1.DLT",
                "bet.voided.v1", "bet.voided.v1.DLT",
                "bet.resolution.revised.v1", "bet.resolution.revised.v1.DLT"));
  }

  @Test
  void retainsTheSourcePartitionAndRejectsUnknownTopics() {
    assertThat(topics.deadLetterDestination("bet.settled.v1", 3).topic())
        .isEqualTo("bet.settled.v1.DLT");
    assertThat(topics.deadLetterDestination("bet.settled.v1", 3).partition()).isEqualTo(3);
    assertThatThrownBy(() -> topics.deadLetterDestination("unknown", 0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsAmbiguousInputInventories() {
    assertThatThrownBy(
            () ->
                new GatewayTopicProperties(
                    "odds.changed", "odds.changed", "bet.voided.v1", "bet.resolution.revised.v1"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new GatewayTopicProperties(
                    "odds.changed.DLT",
                    "bet.settled.v1",
                    "bet.voided.v1",
                    "bet.resolution.revised.v1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not itself be a DLT");
  }
}
