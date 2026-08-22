package com.sportsbook.settlement.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.protocol.value.Money;
import org.junit.jupiter.api.Test;

class SettlementMoneyPlanTest {

  @Test
  void preservesSystemExposureSeparatelyFromUnitStake() {
    SettlementMoneyPlan plan =
        new SettlementMoneyPlan(
            Money.krw(3000), Money.krw(26000), Money.krw(2000), Money.krw(1000), Money.krw(24000));

    assertThat(plan.committed()).isEqualTo(Money.krw(3000));
    assertThat(plan.payout()).isEqualTo(Money.krw(26000));
  }

  @Test
  void rejectsEitherConservationViolation() {
    assertThatThrownBy(
            () ->
                new SettlementMoneyPlan(
                    Money.krw(3000),
                    Money.krw(26000),
                    Money.krw(1000),
                    Money.krw(1000),
                    Money.krw(25000)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
