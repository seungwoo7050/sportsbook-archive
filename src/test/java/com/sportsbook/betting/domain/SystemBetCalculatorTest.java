package com.sportsbook.betting.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.domain.BetSlipType;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.Odds;
import java.util.List;
import org.junit.jupiter.api.Test;

class SystemBetCalculatorTest {

  private final SystemBetCalculator calculator = new SystemBetCalculator();

  @Test
  void multipliesUnitStakeByCombinationCount() {
    BetSlipType.System system = new BetSlipType.System(2, 4);

    assertThat(calculator.lineCount(system, 4)).isEqualTo(6);
    assertThat(calculator.totalStake(system, Money.krw(1_000), 4)).isEqualTo(Money.krw(6_000));
  }

  @Test
  void sumsWinningSystemLinesUsingUnitStake() {
    Money payout =
        calculator.maxPayout(
            new BetSlipType.System(2, 3),
            Money.krw(100),
            List.of(Odds.ofDecimal("2.0"), Odds.ofDecimal("3.0"), Odds.ofDecimal("4.0")));

    assertThat(payout).isEqualTo(Money.krw(2_600));
  }
}
