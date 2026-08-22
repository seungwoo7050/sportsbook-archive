package com.sportsbook.settlement.correction;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ResultCandidateStateTest {

  @Test
  void pendingCandidateCanBeDecidedExactlyOnce() {
    assertThat(ResultCandidateState.PENDING.canTransitionTo(ResultCandidateState.ACCEPTED))
        .isTrue();
    assertThat(ResultCandidateState.PENDING.canTransitionTo(ResultCandidateState.REJECTED))
        .isTrue();
    assertThat(ResultCandidateState.PENDING.canTransitionTo(ResultCandidateState.SUPERSEDED))
        .isTrue();
    assertThat(ResultCandidateState.PENDING.canTransitionTo(ResultCandidateState.PENDING))
        .isFalse();
  }

  @Test
  void acceptedCandidateCanOnlyBeSuperseded() {
    assertThat(ResultCandidateState.ACCEPTED.canTransitionTo(ResultCandidateState.SUPERSEDED))
        .isTrue();
    assertThat(ResultCandidateState.ACCEPTED.canTransitionTo(ResultCandidateState.REJECTED))
        .isFalse();
    assertThat(ResultCandidateState.SUPERSEDED.canTransitionTo(ResultCandidateState.ACCEPTED))
        .isFalse();
    assertThat(ResultCandidateState.REJECTED.canTransitionTo(ResultCandidateState.ACCEPTED))
        .isFalse();
  }
}
