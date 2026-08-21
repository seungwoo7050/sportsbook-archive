package com.sportsbook.risk.event;

/** Stable DLT classifications for permanently unprocessable accepted-bet events. */
public enum BetPlacedFailureReason {
  MALFORMED_EVENT,
  KEY_MISMATCH,
  FINGERPRINT_MISMATCH,
  TERMINAL_RESERVATION;

  static BetPlacedFailureReason fromDecodeFailure(RuntimeException failure) {
    if (failure instanceof BetPlacedKeyMismatchException) {
      return KEY_MISMATCH;
    }
    return MALFORMED_EVENT;
  }
}
