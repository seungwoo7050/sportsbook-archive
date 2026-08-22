package com.sportsbook.betting.config;

public final class BettingTopics {

  public static final String BET_PLACED = "bet.placed.v1";
  public static final String BET_SETTLED = "bet.settled.v1";
  public static final String BET_VOIDED = "bet.voided.v1";
  public static final String BET_RESOLUTION_REVISED = "bet.resolution.revised.v1";
  public static final String WALLET_DEBITED = "wallet.debited.v1";
  public static final String WALLET_DEBIT_FAILED = "wallet.debit-failed.v1";

  private BettingTopics() {}
}
