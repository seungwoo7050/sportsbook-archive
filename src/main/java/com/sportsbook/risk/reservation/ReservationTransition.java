package com.sportsbook.risk.reservation;

/** Exact result of an idempotent commit or release state transition. */
public enum ReservationTransition {
  APPLIED(true, false),
  REPLAYED(true, true),
  NOT_FOUND(false, false),
  EXPIRED(false, false),
  TOMBSTONED(false, false),
  CONFLICT(false, false);

  private final boolean successful;
  private final boolean replayed;

  ReservationTransition(boolean successful, boolean replayed) {
    this.successful = successful;
    this.replayed = replayed;
  }

  public boolean successful() {
    return successful;
  }

  public boolean replayed() {
    return replayed;
  }
}
