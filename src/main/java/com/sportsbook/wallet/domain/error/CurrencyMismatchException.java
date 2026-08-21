package com.sportsbook.wallet.domain.error;

import com.sportsbook.protocol.value.Currency;

/** Raised before a monetary operation crosses currency boundaries. */
public final class CurrencyMismatchException extends RuntimeException {

  private final Currency expected;
  private final Currency actual;

  public CurrencyMismatchException(Currency expected, Currency actual) {
    super("Expected currency " + expected + " but received " + actual);
    this.expected = expected;
    this.actual = actual;
  }

  public Currency expected() {
    return expected;
  }

  public Currency actual() {
    return actual;
  }
}
