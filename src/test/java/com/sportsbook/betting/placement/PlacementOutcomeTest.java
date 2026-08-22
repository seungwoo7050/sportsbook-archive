package com.sportsbook.betting.placement;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PlacementOutcomeTest {

  @Test
  void distinguishesBetPointerFromPreflightVerdict() {
    assertThat(PlacementOutcome.BET.hasBet()).isTrue();
    assertThat(PlacementOutcome.REJECTION.hasBet()).isFalse();
  }
}
