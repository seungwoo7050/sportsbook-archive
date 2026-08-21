package com.sportsbook.oddsfeed.delivery;

import java.util.UUID;

/** The durable result of accepting an operator action. */
public record OperatorActionSubmission(
    Outcome outcome, UUID actionId, long sequence, long predecessor, String recordId) {

  private static final int FIELD_COUNT = 5;

  static OperatorActionSubmission fromRedis(String result) {
    if (result == null) {
      throw new IllegalStateException("Operator action submission returned no result");
    }
    String[] fields = result.split("\\|", -1);
    if (fields.length != FIELD_COUNT) {
      throw new IllegalStateException("Malformed operator action submission result");
    }
    Outcome outcome =
        switch (fields[0]) {
          case "CREATED" -> Outcome.CREATED;
          case "REPLAY" -> Outcome.REPLAYED;
          default -> throw new IllegalStateException("Unknown operator action submission result");
        };
    return new OperatorActionSubmission(
        outcome,
        UUID.fromString(fields[1]),
        Long.parseLong(fields[2]),
        Long.parseLong(fields[FIELD_COUNT - 2]),
        fields[FIELD_COUNT - 1]);
  }

  public enum Outcome {
    CREATED,
    REPLAYED
  }
}
