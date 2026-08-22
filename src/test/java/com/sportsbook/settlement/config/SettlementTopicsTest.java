package com.sportsbook.settlement.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SettlementTopicsTest {

  @Test
  void suppliesFixedContractNames() {
    SettlementTopics topics = new SettlementTopics(null, null, null, null, null, null);

    assertThat(topics.betPlaced()).isEqualTo("bet.placed.v1");
    assertThat(topics.betResolutionRevised()).isEqualTo("bet.resolution.revised.v1");
    assertThat(topics.deadLetter(topics.matchResult())).isEqualTo("match.result.DLT");
  }

  @Test
  void rejectsTopicAliasing() {
    assertThatThrownBy(() -> new SettlementTopics("same", "same", null, null, null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
