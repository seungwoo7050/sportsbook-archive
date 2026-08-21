package com.sportsbook.wallet.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.Money;
import org.junit.jupiter.api.Test;

class WalletFailureSnapshotTest {

  @Test
  void preservesStatusTitleDetailAndBalance() {
    WalletFailureSnapshot snapshot =
        WalletFailureSnapshot.withBalance(
            WalletFailureCode.INSUFFICIENT_BALANCE, "requested 100, available 40", Money.krw(40L));

    assertThat(snapshot.code()).isEqualTo(WalletFailureCode.INSUFFICIENT_BALANCE);
    assertThat(snapshot.httpStatus()).isEqualTo(422);
    assertThat(snapshot.title()).isEqualTo("Insufficient balance");
    assertThat(snapshot.detail()).isEqualTo("requested 100, available 40");
    assertThat(snapshot.balance()).isEqualTo(Money.krw(40L));
    assertThat(snapshot.expectedCurrency()).isNull();
  }

  @Test
  void preservesTheExpectedCurrencyForMismatchReplay() {
    WalletFailureSnapshot snapshot =
        WalletFailureSnapshot.currencyMismatch("expected KRW, received USD", Currency.KRW);

    assertThat(snapshot.code()).isEqualTo(WalletFailureCode.CURRENCY_MISMATCH);
    assertThat(snapshot.httpStatus()).isEqualTo(422);
    assertThat(snapshot.title()).isEqualTo("Currency mismatch");
    assertThat(snapshot.detail()).isEqualTo("expected KRW, received USD");
    assertThat(snapshot.balance()).isNull();
    assertThat(snapshot.expectedCurrency()).isEqualTo(Currency.KRW);
  }

  @Test
  void constructsFailuresWithoutAuxiliaryFacts() {
    WalletFailureSnapshot snapshot =
        WalletFailureSnapshot.of(WalletFailureCode.ACCOUNT_NOT_FOUND, "missing account");

    assertThat(snapshot.code()).isEqualTo(WalletFailureCode.ACCOUNT_NOT_FOUND);
    assertThat(snapshot.detail()).isEqualTo("missing account");
    assertThat(snapshot.balance()).isNull();
    assertThat(snapshot.expectedCurrency()).isNull();
    assertThatNullPointerException()
        .isThrownBy(() -> WalletFailureSnapshot.of(null, "missing account"));
    assertThatNullPointerException()
        .isThrownBy(() -> WalletFailureSnapshot.of(WalletFailureCode.ACCOUNT_NOT_FOUND, null));
  }
}
