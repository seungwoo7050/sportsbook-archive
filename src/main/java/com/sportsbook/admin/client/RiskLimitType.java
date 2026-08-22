package com.sportsbook.admin.client;

public enum RiskLimitType {
  STAKE_DAILY,
  STAKE_WEEKLY,
  STAKE_MONTHLY,
  SELECTIONS_PER_MINUTE;

  public boolean requiresCurrency() {
    return this != SELECTIONS_PER_MINUTE;
  }
}
