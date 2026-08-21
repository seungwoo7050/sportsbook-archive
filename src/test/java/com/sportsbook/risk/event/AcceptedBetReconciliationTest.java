package com.sportsbook.risk.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AcceptedBetReconciliationTest {
  @Test
  void exposesOnlyPermanentReservationFailuresAsDeadLetterReasons() {
    assertThat(AcceptedBetReconciliation.CONFIRMED.permanentFailure()).isFalse();
    assertThat(AcceptedBetReconciliation.PROJECTED.permanentFailure()).isFalse();
    assertThat(AcceptedBetReconciliation.REPLAYED.permanentFailure()).isFalse();
    assertThat(AcceptedBetReconciliation.FINGERPRINT_MISMATCH.failureReason())
        .isEqualTo(BetPlacedFailureReason.FINGERPRINT_MISMATCH);
    assertThat(AcceptedBetReconciliation.TERMINAL_RESERVATION.failureReason())
        .isEqualTo(BetPlacedFailureReason.TERMINAL_RESERVATION);
    assertThatThrownBy(AcceptedBetReconciliation.CONFIRMED::failureReason)
        .isInstanceOf(IllegalStateException.class);
  }
}
