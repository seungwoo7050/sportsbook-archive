package com.sportsbook.settlement.client;

public enum WalletCreditPurpose {
  WHOLE_SLIP_VOID("USER_LOCKED", "VOID", "BET_REFUND"),
  RETURNED_STAKE("USER_LOCKED", "REFUND", "BET_REFUND"),
  PROFIT_PAYOUT("HOUSE_POOL", "PAYOUT", "BET_PAYOUT");

  private final String source;
  private final String reason;
  private final String proofReason;

  WalletCreditPurpose(String source, String reason, String proofReason) {
    this.source = source;
    this.reason = reason;
    this.proofReason = proofReason;
  }

  public String source() {
    return source;
  }

  public String reason() {
    return reason;
  }

  public String proofReason() {
    return proofReason;
  }
}
