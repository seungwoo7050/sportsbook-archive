package com.sportsbook.wallet.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import org.junit.jupiter.api.Test;

class WalletErrorTest {
  private static final String BASE = "https://sportsbook/errors/wallet/";

  @Test
  void locksWalletErrorVocabulary() {
    assertThat(WalletError.values())
        .extracting(
            WalletError::httpStatus,
            WalletError::errorCode,
            WalletError::title,
            error -> error.type().toString())
        .containsExactly(
            tuple(
                400, "WALLET_INVALID_REQUEST", "Invalid wallet request", BASE + "invalid-request"),
            tuple(
                401,
                "WALLET_AUTHENTICATION_REQUIRED",
                "Authentication required",
                BASE + "authentication-required"),
            tuple(403, "WALLET_ACCESS_DENIED", "Wallet access denied", BASE + "access-denied"),
            tuple(404, "WALLET_ACCOUNT_NOT_FOUND", "Account not found", BASE + "account-not-found"),
            tuple(
                404,
                "WALLET_OPERATION_NOT_FOUND",
                "Wallet operation not found",
                BASE + "operation-not-found"),
            tuple(
                404,
                "WALLET_ADJUSTMENT_NOT_FOUND",
                "Wallet adjustment not found",
                BASE + "adjustment-not-found"),
            tuple(
                409,
                "WALLET_IDEMPOTENCY_CONFLICT",
                "Idempotency key conflict",
                BASE + "idempotency-conflict"),
            tuple(422, "WALLET_CURRENCY_MISMATCH", "Currency mismatch", BASE + "currency-mismatch"),
            tuple(
                422,
                "WALLET_INSUFFICIENT_BALANCE",
                "Insufficient balance",
                BASE + "insufficient-balance"),
            tuple(
                422,
                "WALLET_AMOUNT_OUT_OF_RANGE",
                "Amount out of range",
                BASE + "amount-out-of-range"),
            tuple(
                423,
                "WALLET_ACCOUNT_RECOVERY_BLOCKED",
                "Wallet account blocked for recovery",
                BASE + "account-recovery-blocked"),
            tuple(500, "WALLET_INTERNAL_ERROR", "Internal server error", BASE + "internal-error"),
            tuple(503, "WALLET_BUSY", "Wallet temporarily busy", BASE + "busy"));
  }
}
