package com.sportsbook.risk.event;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BetPlacedFailureReasonTest {
  @Test
  void distinguishesPartitionKeyViolationsFromOtherMalformedEvents() {
    assertThat(BetPlacedFailureReason.fromDecodeFailure(new BetPlacedKeyMismatchException()))
        .isEqualTo(BetPlacedFailureReason.KEY_MISMATCH);
    assertThat(
            BetPlacedFailureReason.fromDecodeFailure(
                new IllegalArgumentException("selectionIds must be unique")))
        .isEqualTo(BetPlacedFailureReason.MALFORMED_EVENT);
  }
}
