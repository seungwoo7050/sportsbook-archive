package com.sportsbook.settlement.resolver;

import com.sportsbook.protocol.value.Money;
import java.util.Objects;

/** Monetary result of line evaluation before slip-level classification. */
public record PayoutCalculation(Money payout, int survivingLines, int totalLines) {

  public PayoutCalculation {
    Objects.requireNonNull(payout, "payout");
    if (payout.isNegative()
        || totalLines < 1
        || survivingLines < 0
        || survivingLines > totalLines) {
      throw new IllegalArgumentException("Invalid payout line calculation");
    }
  }
}
