package com.sportsbook.wallet.service;

import com.sportsbook.protocol.value.Money;
import com.sportsbook.wallet.domain.WalletFailureCode;
import com.sportsbook.wallet.domain.WalletFailureSnapshot;
import com.sportsbook.wallet.domain.error.AccountNotFoundException;
import com.sportsbook.wallet.domain.error.BalanceLimitExceededException;
import com.sportsbook.wallet.domain.error.CurrencyMismatchException;
import com.sportsbook.wallet.domain.error.InsufficientBalanceException;

/** Converts expected business failures into immutable replay snapshots; infrastructure escapes. */
final class WalletFailureMapper {

  static WalletFailureSnapshot snapshot(RuntimeException failure, Money requestAmount) {
    if (failure instanceof AccountNotFoundException) {
      return WalletFailureSnapshot.of(WalletFailureCode.ACCOUNT_NOT_FOUND, failure.getMessage());
    }
    if (failure instanceof CurrencyMismatchException mismatch) {
      return WalletFailureSnapshot.currencyMismatch(failure.getMessage(), mismatch.expected());
    }
    if (failure instanceof InsufficientBalanceException) {
      return WalletFailureSnapshot.of(WalletFailureCode.INSUFFICIENT_BALANCE, failure.getMessage());
    }
    if (failure instanceof BalanceLimitExceededException limit) {
      long current = Math.addExact(limit.availableAmount(), limit.lockedAmount());
      return WalletFailureSnapshot.withBalance(
          WalletFailureCode.AMOUNT_OUT_OF_RANGE,
          failure.getMessage(),
          new Money(current, requestAmount.currency()));
    }
    throw failure;
  }

  private WalletFailureMapper() {}
}
