package com.sportsbook.settlement.result;

import com.sportsbook.settlement.domain.Bet;
import com.sportsbook.settlement.execution.SettlementAttemptDraft;
import com.sportsbook.settlement.execution.SettlementMoneyPlan;
import com.sportsbook.settlement.resolver.ResolvedSelection;
import com.sportsbook.settlement.resolver.SettlementResolver;
import com.sportsbook.settlement.resolver.WalletMovementPlanner;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class BaseSettlementPlanner {

  private final SettlementResolver resolver = new SettlementResolver();
  private final WalletMovementPlanner movements = new WalletMovementPlanner();

  public SettlementAttemptDraft plan(Bet bet, UUID drivingEventId) {
    if (!bet.allSelectionsResolved()) {
      throw new IllegalArgumentException("Base settlement requires every selection outcome");
    }
    var selections =
        bet.selections().stream()
            .map(
                selection ->
                    new ResolvedSelection(
                        selection.selectionId(), selection.odds(), selection.outcome()))
            .toList();
    var outcome = resolver.resolve(bet.slipType(), selections, bet.stake());
    var movement = movements.plan(bet.stake(), outcome);
    var money =
        new SettlementMoneyPlan(
            movement.totalExposure(),
            outcome.payout(),
            movement.returnedStake(),
            movement.forfeitedStake(),
            movement.housePayout());
    return SettlementAttemptDraft.resolved(bet.betId(), drivingEventId, outcome.result(), money);
  }
}
