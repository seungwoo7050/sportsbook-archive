package com.sportsbook.settlement.correction;

/** Durable lifecycle for one immutable correction plan. */
public enum RevisionState {
  PENDING,
  BLOCKED,
  EXHAUSTED,
  APPLIED,
  REJECTED;

  public boolean canTransitionTo(RevisionState target) {
    if (this == target) {
      return true;
    }
    return switch (this) {
      case PENDING ->
          target == BLOCKED || target == EXHAUSTED || target == APPLIED || target == REJECTED;
      case BLOCKED -> target == PENDING || target == APPLIED || target == REJECTED;
      case EXHAUSTED, REJECTED, APPLIED -> false;
    };
  }

  public void requireTransitionTo(RevisionState target) {
    if (!canTransitionTo(target)) {
      throw new IllegalStateException(
          "Invalid revision state transition: " + this + " -> " + target);
    }
  }
}
