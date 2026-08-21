package com.sportsbook.wallet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.wallet.domain.WalletFailureCode;
import com.sportsbook.wallet.domain.WalletFailureSnapshot;
import com.sportsbook.wallet.domain.error.AccountNotFoundException;
import com.sportsbook.wallet.domain.error.AccountRecoveryBlockedException;
import com.sportsbook.wallet.domain.error.BalanceLimitExceededException;
import com.sportsbook.wallet.domain.error.CurrencyMismatchException;
import com.sportsbook.wallet.domain.error.InsufficientBalanceException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WalletFailureMapperTest {
  private static final UUID USER_ID = UUID.fromString("019b76da-a000-7000-8000-000000000024");

  @Test
  void snapshotsOnlyTerminalBusinessFailures() {
    WalletFailureSnapshot missing =
        WalletFailureMapper.snapshot(new AccountNotFoundException(USER_ID), Money.krw(10L));
    assertThat(missing.code()).isEqualTo(WalletFailureCode.ACCOUNT_NOT_FOUND);

    WalletFailureSnapshot frozen =
        WalletFailureMapper.snapshot(new AccountRecoveryBlockedException(USER_ID), Money.krw(10L));
    assertThat(frozen.code()).isEqualTo(WalletFailureCode.ACCOUNT_SUSPENDED);
    assertThat(frozen.httpStatus()).isEqualTo(423);
    assertThat(frozen.code().wireCode()).isEqualTo("WALLET_ACCOUNT_RECOVERY_BLOCKED");

    WalletFailureSnapshot insufficient =
        WalletFailureMapper.snapshot(
            new InsufficientBalanceException(USER_ID, Money.krw(10L), Money.krw(5L)),
            Money.krw(10L));
    assertThat(insufficient.code()).isEqualTo(WalletFailureCode.INSUFFICIENT_BALANCE);

    CurrencyMismatchException mismatch = new CurrencyMismatchException(Currency.KRW, Currency.USD);
    WalletFailureSnapshot currency = WalletFailureMapper.snapshot(mismatch, Money.usd(10L));
    assertThat(currency.code()).isEqualTo(WalletFailureCode.CURRENCY_MISMATCH);
    assertThat(currency.expectedCurrency()).isEqualTo(Currency.KRW);

    BalanceLimitExceededException limit =
        new BalanceLimitExceededException(USER_ID, Long.MAX_VALUE - 4L, 4L);
    WalletFailureSnapshot aggregate = WalletFailureMapper.snapshot(limit, Money.krw(1L));
    assertThat(aggregate.code()).isEqualTo(WalletFailureCode.AMOUNT_OUT_OF_RANGE);
    assertThat(aggregate.balance()).isEqualTo(Money.krw(Long.MAX_VALUE));

    RuntimeException infrastructure = new RuntimeException("database unavailable");
    assertThat(catchThrowable(() -> WalletFailureMapper.snapshot(infrastructure, Money.krw(1L))))
        .isSameAs(infrastructure);
  }
}
