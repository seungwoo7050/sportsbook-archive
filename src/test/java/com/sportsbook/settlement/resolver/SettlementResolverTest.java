package com.sportsbook.settlement.resolver;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.domain.BetSlipType;
import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.Odds;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SettlementResolverTest {

  private final SettlementResolver resolver = new SettlementResolver();

  @Test
  void classifiesStraightWinLossAndPush() {
    assertThat(resolve(SettlementResult.WON).result()).isEqualTo(SettlementResult.WON);
    assertThat(resolve(SettlementResult.LOST).result()).isEqualTo(SettlementResult.LOST);
    assertThat(resolve(SettlementResult.PUSH).result()).isEqualTo(SettlementResult.PUSH);
    assertThat(resolve(SettlementResult.PUSH).payout()).isEqualTo(Money.krw(100));
  }

  @Test
  void allVoidIsASettledResultVoidNotAWholeSlipLifecycleVoid() {
    SettlementOutcome outcome = resolve(SettlementResult.VOID);

    assertThat(outcome.result()).isEqualTo(SettlementResult.VOID);
    assertThat(outcome.payout()).isEqualTo(Money.krw(100));
  }

  @Test
  void classifiesAPartiallyWinningSystemFromItsSurvivingLine() {
    List<ResolvedSelection> selections =
        List.of(
            selection("2.0000", SettlementResult.WON),
            selection("3.0000", SettlementResult.WON),
            selection("4.0000", SettlementResult.LOST));

    SettlementOutcome outcome =
        resolver.resolve(new BetSlipType.System(2, 3), selections, Money.krw(1_000));

    assertThat(outcome.result()).isEqualTo(SettlementResult.WON);
    assertThat(outcome.payout()).isEqualTo(Money.krw(6_000));
    assertThat(outcome.survivingLines()).isOne();
  }

  private SettlementOutcome resolve(SettlementResult result) {
    return resolver.resolve(
        new BetSlipType.Single(), List.of(selection("2.0000", result)), Money.krw(100));
  }

  private static ResolvedSelection selection(String odds, SettlementResult result) {
    return new ResolvedSelection(UUID.randomUUID(), Odds.ofDecimal(odds), result);
  }
}
