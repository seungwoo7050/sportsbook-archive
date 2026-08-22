package com.sportsbook.settlement.correction;

import com.sportsbook.settlement.resolver.SettlementOutcome;
import com.sportsbook.settlement.resolver.SettlementResolver;

/** Reuses base payout rules against an immutable replacement snapshot. */
public final class RevisionResolver {

  private final SettlementResolver settlements = new SettlementResolver();

  public SettlementOutcome resolve(RevisionTarget target) {
    return settlements.resolve(target.slipType(), target.selections(), target.unitStake());
  }
}
