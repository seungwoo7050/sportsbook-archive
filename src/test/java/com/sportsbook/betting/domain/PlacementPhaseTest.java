package com.sportsbook.betting.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PlacementPhaseTest {

  @Test
  void ordersOnlyForwardProgress() {
    assertThat(PlacementPhase.CREATED.precedes(PlacementPhase.RISK_RESERVED)).isTrue();
    assertThat(PlacementPhase.RISK_COMMITTED.precedes(PlacementPhase.CREATED)).isFalse();
  }
}
