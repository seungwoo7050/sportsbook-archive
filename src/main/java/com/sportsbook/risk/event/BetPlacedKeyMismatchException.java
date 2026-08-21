package com.sportsbook.risk.event;

/** Identifies a Kafka partition-key contract violation independently of malformed payloads. */
final class BetPlacedKeyMismatchException extends IllegalArgumentException {
  BetPlacedKeyMismatchException() {
    super("Kafka key must equal userId");
  }
}
