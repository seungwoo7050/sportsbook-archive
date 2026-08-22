package com.sportsbook.settlement.correction;

public enum ResultCandidateState {
  PENDING,
  ACCEPTED,
  SUPERSEDED,
  REJECTED;

  public boolean canTransitionTo(ResultCandidateState target) {
    if (target == null || target == this) {
      return false;
    }
    return switch (this) {
      case PENDING -> target == ACCEPTED || target == SUPERSEDED || target == REJECTED;
      case ACCEPTED -> target == SUPERSEDED;
      case SUPERSEDED, REJECTED -> false;
    };
  }
}
