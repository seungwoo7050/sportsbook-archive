package com.sportsbook.risk.event;

/** Atomic result of reconciling an accepted event with reservation and committed state. */
public enum AcceptedBetReconciliation {
  CONFIRMED(null),
  PROJECTED(null),
  REPLAYED(null),
  FINGERPRINT_MISMATCH(BetPlacedFailureReason.FINGERPRINT_MISMATCH),
  TERMINAL_RESERVATION(BetPlacedFailureReason.TERMINAL_RESERVATION);

  private final BetPlacedFailureReason failureReason;

  AcceptedBetReconciliation(BetPlacedFailureReason failureReason) {
    this.failureReason = failureReason;
  }

  public boolean permanentFailure() {
    return failureReason != null;
  }

  public BetPlacedFailureReason failureReason() {
    if (failureReason == null) {
      throw new IllegalStateException("successful reconciliation has no failure reason");
    }
    return failureReason;
  }
}
