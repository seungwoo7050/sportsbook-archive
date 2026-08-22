package com.sportsbook.settlement.client;

public enum WalletCreditPurpose {
  WHOLE_SLIP_VOID("USER_LOCKED", "VOID"),
  RETURNED_STAKE("USER_LOCKED", "REFUND"),
  PROFIT_PAYOUT("HOUSE_POOL", "PAYOUT");

  private final String source;
  private final String reason;

  WalletCreditPurpose(String source, String reason) {
    this.source = source;
    this.reason = reason;
  }

  public String source() {
    return source;
  }

  public String reason() {
    return reason;
  }
}
