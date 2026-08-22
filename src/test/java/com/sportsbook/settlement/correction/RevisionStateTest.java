package com.sportsbook.settlement.correction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import org.junit.jupiter.api.Test;

class RevisionStateTest {

  @Test
  void permitsWalletOutcomesAndExplicitOperatorRetry() {
    assertThat(RevisionState.PENDING.canTransitionTo(RevisionState.BLOCKED)).isTrue();
    assertThat(RevisionState.PENDING.canTransitionTo(RevisionState.EXHAUSTED)).isTrue();
    assertThat(RevisionState.PENDING.canTransitionTo(RevisionState.APPLIED)).isTrue();
    assertThat(RevisionState.PENDING.canTransitionTo(RevisionState.REJECTED)).isTrue();
    assertThat(RevisionState.BLOCKED.canTransitionTo(RevisionState.APPLIED)).isTrue();
    assertThat(RevisionState.BLOCKED.canTransitionTo(RevisionState.REJECTED)).isTrue();
    assertThat(RevisionState.REJECTED.canTransitionTo(RevisionState.PENDING)).isFalse();
    assertThat(RevisionState.EXHAUSTED.canTransitionTo(RevisionState.PENDING)).isFalse();
  }

  @Test
  void keepsAppliedRevisionsTerminal() {
    assertThat(RevisionState.APPLIED.canTransitionTo(RevisionState.APPLIED)).isTrue();
    assertThatIllegalStateException()
        .isThrownBy(() -> RevisionState.APPLIED.requireTransitionTo(RevisionState.PENDING));
  }
}
