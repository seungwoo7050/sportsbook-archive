package com.sportsbook.settlement.resolver;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.domain.BetSlipType;
import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.Odds;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SettlementPayoutCalculatorTest {

  private final SettlementLineFactory lines = new SettlementLineFactory();
  private final SettlementPayoutCalculator calculator = new SettlementPayoutCalculator();

  @Test
  void sumsSystemLineProductsBeforeOneFinalFloor() {
    List<ResolvedSelection> selections =
        List.of(
            selection("1.5000", SettlementResult.WON), selection("1.5000", SettlementResult.WON));

    PayoutCalculation payout =
        calculator.calculate(lines.lines(new BetSlipType.System(1, 2), selections), Money.krw(1));

    assertThat(payout.payout()).isEqualTo(Money.krw(3));
    assertThat(payout.survivingLines()).isEqualTo(2);
    assertThat(payout.totalLines()).isEqualTo(2);
  }

  @Test
  void killsLostLinesAndTreatsPushOrVoidAsNeutral() {
    List<ResolvedSelection> selections =
        List.of(
            selection("2.0000", SettlementResult.WON),
            selection("3.0000", SettlementResult.LOST),
            selection("4.0000", SettlementResult.VOID));
    List<SettlementLine> system = lines.lines(new BetSlipType.System(2, 3), selections);

    PayoutCalculation payout = calculator.calculate(system, Money.krw(1_000));

    assertThat(payout.payout()).isEqualTo(Money.krw(2_000));
    assertThat(payout.survivingLines()).isOne();
  }

  private static ResolvedSelection selection(String odds, SettlementResult result) {
    return new ResolvedSelection(UUID.randomUUID(), Odds.ofDecimal(odds), result);
  }
}
