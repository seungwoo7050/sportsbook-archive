package com.sportsbook.wallet.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import org.junit.jupiter.api.Test;

class WalletFailureCodeTest {

  @Test
  void locksDurableFailureVocabulary() {
    assertThat(WalletFailureCode.values())
        .extracting(
            Enum::name,
            WalletFailureCode::httpStatus,
            WalletFailureCode::wireCode,
            WalletFailureCode::title)
        .containsExactly(
            tuple("ACCOUNT_NOT_FOUND", 404, "WALLET_ACCOUNT_NOT_FOUND", "Account not found"),
            tuple("CURRENCY_MISMATCH", 422, "WALLET_CURRENCY_MISMATCH", "Currency mismatch"),
            tuple(
                "INSUFFICIENT_BALANCE", 422, "WALLET_INSUFFICIENT_BALANCE", "Insufficient balance"),
            tuple(
                "ACCOUNT_SUSPENDED",
                423,
                "WALLET_ACCOUNT_RECOVERY_BLOCKED",
                "Wallet account blocked for recovery"),
            tuple("AMOUNT_OUT_OF_RANGE", 422, "WALLET_AMOUNT_OUT_OF_RANGE", "Amount out of range"));
  }
}
