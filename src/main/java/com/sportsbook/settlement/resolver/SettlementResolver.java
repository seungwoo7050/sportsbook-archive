package com.sportsbook.settlement.resolver;

import com.sportsbook.protocol.domain.BetSlipType;
import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.protocol.value.Money;
import java.util.List;

/** Resolves base slip result and payout from authoritative selection outcomes. */
public final class SettlementResolver {

  private final SettlementLineFactory lines = new SettlementLineFactory();
  private final SettlementPayoutCalculator payouts = new SettlementPayoutCalculator();

  public SettlementOutcome resolve(
      BetSlipType slipType, List<ResolvedSelection> selections, Money unitStake) {
    PayoutCalculation payout = payouts.calculate(lines.lines(slipType, selections), unitStake);
    return new SettlementOutcome(
        classify(selections, payout),
        payout.payout(),
        payout.survivingLines(),
        payout.totalLines());
  }

  private static SettlementResult classify(
      List<ResolvedSelection> selections, PayoutCalculation payout) {
    if (payout.payout().isZero()) {
      return SettlementResult.LOST;
    }
    if (selections.stream().anyMatch(ResolvedSelection::wins)) {
      return SettlementResult.WON;
    }
    boolean allVoid =
        selections.stream().allMatch(selection -> selection.outcome() == SettlementResult.VOID);
    return allVoid ? SettlementResult.VOID : SettlementResult.PUSH;
  }
}
