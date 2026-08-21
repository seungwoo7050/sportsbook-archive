package com.sportsbook.oddsfeed.delivery;

import java.util.UUID;

/** The durable result of accepting an operator action. */
public record OperatorActionSubmission(
    Outcome outcome, UUID actionId, long sequence, long predecessor, String recordId) {

  public enum Outcome {
    CREATED,
    REPLAYED
  }
}
