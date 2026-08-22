package com.sportsbook.settlement.resolver;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.protocol.value.Money;
import org.junit.jupiter.api.Test;

class WalletMovementPlannerTest {

  private final WalletMovementPlanner planner = new WalletMovementPlanner();

  @Test
  void keepsUnitStakeSeparateFromSystemTotalExposure() {
    Money unitStake = Money.krw(1_000);
    SettlementOutcome outcome = new SettlementOutcome(SettlementResult.WON, Money.krw(6_000), 1, 3);

    WalletMovementPlan plan = planner.plan(unitStake, outcome);

    assertThat(unitStake).isEqualTo(Money.krw(1_000));
    assertThat(plan.totalExposure()).isEqualTo(Money.krw(3_000));
    assertThat(plan.returnedStake()).isEqualTo(Money.krw(1_000));
    assertThat(plan.housePayout()).isEqualTo(Money.krw(5_000));
    assertThat(plan.forfeitedStake()).isEqualTo(Money.krw(2_000));
  }

  @Test
  void returnsEveryLineWithoutHouseProfitForAFullVoidResult() {
    SettlementOutcome outcome =
        new SettlementOutcome(SettlementResult.VOID, Money.krw(3_000), 3, 3);

    WalletMovementPlan plan = planner.plan(Money.krw(1_000), outcome);

    assertThat(plan.returnedStake()).isEqualTo(Money.krw(3_000));
    assertThat(plan.housePayout()).isEqualTo(Money.krw(0));
    assertThat(plan.forfeitedStake()).isEqualTo(Money.krw(0));
  }
}
