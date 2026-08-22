package com.sportsbook.settlement.execution;

import com.sportsbook.protocol.value.Money;
import java.util.List;
import java.util.Objects;

public record SettlementMoneyPlan(
    Money committed, Money payout, Money lockedRelease, Money lockedForfeit, Money houseProfit) {

  public SettlementMoneyPlan {
    List<Money> values =
        List.of(
            Objects.requireNonNull(committed, "committed"),
            Objects.requireNonNull(payout, "payout"),
            Objects.requireNonNull(lockedRelease, "lockedRelease"),
            Objects.requireNonNull(lockedForfeit, "lockedForfeit"),
            Objects.requireNonNull(houseProfit, "houseProfit"));
    if (values.stream().anyMatch(Money::isNegative)
        || values.stream().map(Money::currency).distinct().count() != 1
        || !lockedRelease.add(lockedForfeit).equals(committed)
        || !lockedRelease.add(houseProfit).equals(payout)) {
      throw new IllegalArgumentException("Settlement money plan violates conservation");
    }
  }
}
