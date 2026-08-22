package com.sportsbook.betting.domain;

public enum PlacementPhase {
  CREATED,
  RISK_RESERVED,
  WALLET_CONFIRMED,
  RISK_COMMITTED;

  public boolean precedes(PlacementPhase other) {
    return ordinal() < other.ordinal();
  }
}
