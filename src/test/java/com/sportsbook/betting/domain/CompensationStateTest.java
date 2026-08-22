package com.sportsbook.betting.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CompensationStateTest {

  @Test
  void identifiesRetryableCompensationStates() {
    assertThat(CompensationState.REQUIRED.pending()).isTrue();
    assertThat(CompensationState.IN_PROGRESS.pending()).isTrue();
    assertThat(CompensationState.COMPLETED.pending()).isFalse();
    assertThat(CompensationAction.values()).contains(CompensationAction.WALLET_REFUND);
  }
}
