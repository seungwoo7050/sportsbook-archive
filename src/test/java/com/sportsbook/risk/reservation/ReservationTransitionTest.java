package com.sportsbook.risk.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReservationTransitionTest {
  @Test
  void distinguishesAppliedAndReplaySuccess() {
    assertThat(ReservationTransition.APPLIED.successful()).isTrue();
    assertThat(ReservationTransition.APPLIED.replayed()).isFalse();
    assertThat(ReservationTransition.REPLAYED.successful()).isTrue();
    assertThat(ReservationTransition.REPLAYED.replayed()).isTrue();
  }

  @Test
  void keepsEveryTerminalOrConflictResultUnsuccessful() {
    assertThat(
            java.util.List.of(
                ReservationTransition.NOT_FOUND,
                ReservationTransition.EXPIRED,
                ReservationTransition.TOMBSTONED,
                ReservationTransition.CONFLICT))
        .allSatisfy(transition -> assertThat(transition.successful()).isFalse());
    assertThat(ReservationState.values())
        .containsExactly(ReservationState.RESERVED, ReservationState.COMMITTED);
  }
}
