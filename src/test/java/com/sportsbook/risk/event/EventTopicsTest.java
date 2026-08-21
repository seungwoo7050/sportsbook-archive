package com.sportsbook.risk.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class EventTopicsTest {
  @Test
  void suppliesTheExactOwnedTopicDefaults() {
    EventTopics topics = new EventTopics(null, null, null, null);

    assertThat(topics.betPlaced()).isEqualTo("bet.placed.v1");
    assertThat(topics.betPlacedDlt()).isEqualTo("bet.placed.v1.DLT");
    assertThat(topics.limitViolated()).isEqualTo("risk.limit.violated");
    assertThat(topics.patternSuspected()).isEqualTo("risk.pattern.suspected");
  }

  @Test
  void acceptsExplicitNamesButRejectsBlanks() {
    EventTopics topics = new EventTopics("placed", "dlt", "limit", "pattern");

    assertThat(topics.betPlaced()).isEqualTo("placed");
    assertThat(topics.betPlacedDlt()).isEqualTo("dlt");
    assertThatThrownBy(() -> new EventTopics(" ", "dlt", "limit", "pattern"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
