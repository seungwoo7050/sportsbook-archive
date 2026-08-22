package com.sportsbook.betting.placement;

public enum PlacementOutcome {
  BET,
  REJECTION;

  public boolean hasBet() {
    return this == BET;
  }
}
