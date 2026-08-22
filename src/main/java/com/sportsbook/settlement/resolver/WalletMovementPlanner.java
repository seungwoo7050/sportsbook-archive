package com.sportsbook.settlement.resolver;

import com.sportsbook.protocol.value.Money;

/** Separates unit-stake exposure from returned stake and house-funded profit. */
public final class WalletMovementPlanner {

  public WalletMovementPlan plan(Money unitStake, SettlementOutcome outcome) {
    if (unitStake == null || unitStake.amount() <= 0 || outcome == null) {
      throw new IllegalArgumentException("Wallet movement planning requires a positive unit stake");
    }
    Money totalExposure = unitStake.multiply(outcome.totalLines());
    Money returnedStake = unitStake.multiply(outcome.survivingLines());
    Money forfeitedStake = totalExposure.subtract(returnedStake);
    Money housePayout = outcome.payout().subtract(returnedStake);
    if (housePayout.isNegative()) {
      throw new IllegalArgumentException("Payout cannot be lower than surviving returned stake");
    }
    return new WalletMovementPlan(returnedStake, housePayout, forfeitedStake, totalExposure);
  }
}
