package com.sportsbook.wallet.domain;

/** Stable business failures that may be committed as an exact replay outcome. */
public enum WalletFailureCode {
  ACCOUNT_NOT_FOUND(404, "WALLET_ACCOUNT_NOT_FOUND", "Account not found"),
  CURRENCY_MISMATCH(422, "WALLET_CURRENCY_MISMATCH", "Currency mismatch"),
  INSUFFICIENT_BALANCE(422, "WALLET_INSUFFICIENT_BALANCE", "Insufficient balance"),
  ACCOUNT_SUSPENDED(423, "WALLET_ACCOUNT_RECOVERY_BLOCKED", "Wallet account blocked for recovery"),
  AMOUNT_OUT_OF_RANGE(422, "WALLET_AMOUNT_OUT_OF_RANGE", "Amount out of range");

  private final int httpStatus;
  private final String wireCode;
  private final String title;

  WalletFailureCode(int httpStatus, String wireCode, String title) {
    this.httpStatus = httpStatus;
    this.wireCode = wireCode;
    this.title = title;
  }

  public int httpStatus() {
    return httpStatus;
  }

  public String wireCode() {
    return wireCode;
  }

  public String title() {
    return title;
  }
}
