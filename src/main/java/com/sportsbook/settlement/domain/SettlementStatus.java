package com.sportsbook.settlement.domain;

/** Settlement-owned lifecycle, separate from betting placement state. */
public enum SettlementStatus {
  PENDING,
  SETTLED,
  VOIDED;

  public boolean isTerminal() {
    return this != PENDING;
  }
}
