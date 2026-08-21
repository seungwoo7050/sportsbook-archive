package com.sportsbook.wallet.web;

import java.net.URI;

/** Stable HTTP problem vocabulary for wallet-specific failures. */
public enum WalletError {
  INVALID_REQUEST(400, "WALLET_INVALID_REQUEST", "Invalid wallet request", "invalid-request"),
  AUTHENTICATION_REQUIRED(
      401, "WALLET_AUTHENTICATION_REQUIRED", "Authentication required", "authentication-required"),
  ACCESS_DENIED(403, "WALLET_ACCESS_DENIED", "Wallet access denied", "access-denied"),
  ACCOUNT_NOT_FOUND(404, "WALLET_ACCOUNT_NOT_FOUND", "Account not found", "account-not-found"),
  OPERATION_NOT_FOUND(
      404, "WALLET_OPERATION_NOT_FOUND", "Wallet operation not found", "operation-not-found"),
  ADJUSTMENT_NOT_FOUND(
      404, "WALLET_ADJUSTMENT_NOT_FOUND", "Wallet adjustment not found", "adjustment-not-found"),
  IDEMPOTENCY_CONFLICT(
      409, "WALLET_IDEMPOTENCY_CONFLICT", "Idempotency key conflict", "idempotency-conflict"),
  CURRENCY_MISMATCH(422, "WALLET_CURRENCY_MISMATCH", "Currency mismatch", "currency-mismatch"),
  INSUFFICIENT_BALANCE(
      422, "WALLET_INSUFFICIENT_BALANCE", "Insufficient balance", "insufficient-balance"),
  AMOUNT_OUT_OF_RANGE(
      422, "WALLET_AMOUNT_OUT_OF_RANGE", "Amount out of range", "amount-out-of-range"),
  ACCOUNT_RECOVERY_BLOCKED(
      423,
      "WALLET_ACCOUNT_RECOVERY_BLOCKED",
      "Wallet account blocked for recovery",
      "account-recovery-blocked"),
  INTERNAL_ERROR(500, "WALLET_INTERNAL_ERROR", "Internal server error", "internal-error"),
  WALLET_BUSY(503, "WALLET_BUSY", "Wallet temporarily busy", "busy");

  private static final String TYPE_BASE = "https://sportsbook/errors/wallet/";

  private final int httpStatus;
  private final String errorCode;
  private final String title;
  private final URI type;

  WalletError(int httpStatus, String errorCode, String title, String slug) {
    this.httpStatus = httpStatus;
    this.errorCode = errorCode;
    this.title = title;
    this.type = URI.create(TYPE_BASE + slug);
  }

  public int httpStatus() {
    return httpStatus;
  }

  public String errorCode() {
    return errorCode;
  }

  public String title() {
    return title;
  }

  public URI type() {
    return type;
  }
}
