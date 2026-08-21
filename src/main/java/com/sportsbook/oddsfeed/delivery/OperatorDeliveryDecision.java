package com.sportsbook.oddsfeed.delivery;

import com.sportsbook.protocol.event.MarketStatus;
import java.util.Objects;

/** Current Redis verdict immediately before an operator action is published. */
record OperatorDeliveryDecision(Outcome outcome, MarketStatus announcedStatus) {

  OperatorDeliveryDecision {
    Objects.requireNonNull(outcome, "outcome");
    if ((outcome == Outcome.PUBLISH) != (announcedStatus != null)) {
      throw new IllegalArgumentException("Only publish decisions contain a market status");
    }
  }

  enum Outcome {
    PUBLISH,
    SKIP,
    BLOCKED,
    COMPLETED
  }
}
