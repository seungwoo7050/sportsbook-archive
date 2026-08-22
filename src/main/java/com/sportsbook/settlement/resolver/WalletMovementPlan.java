package com.sportsbook.settlement.resolver;

import com.sportsbook.protocol.value.Money;
import java.util.Objects;

/** Split between locked stake, house pool, and forfeited exposure for one resolution. */
public record WalletMovementPlan(
    Money returnedStake, Money housePayout, Money forfeitedStake, Money totalExposure) {

  public WalletMovementPlan {
    Objects.requireNonNull(returnedStake, "returnedStake");
    Objects.requireNonNull(housePayout, "housePayout");
    Objects.requireNonNull(forfeitedStake, "forfeitedStake");
    Objects.requireNonNull(totalExposure, "totalExposure");
    if (returnedStake.isNegative()
        || housePayout.isNegative()
        || forfeitedStake.isNegative()
        || !returnedStake.add(forfeitedStake).equals(totalExposure)) {
      throw new IllegalArgumentException("Invalid wallet movement split");
    }
  }
}
