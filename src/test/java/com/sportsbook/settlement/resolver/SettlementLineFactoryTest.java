package com.sportsbook.settlement.resolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.sportsbook.protocol.domain.BetSlipType;
import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.protocol.value.Odds;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SettlementLineFactoryTest {

  private final SettlementLineFactory factory = new SettlementLineFactory();

  @Test
  void createsOneUnitStakedLineForSingleAndMultipleSlips() {
    ResolvedSelection first = selection();
    ResolvedSelection second = selection();

    assertThat(factory.lines(new BetSlipType.Single(), List.of(first)))
        .containsExactly(new SettlementLine(0, List.of(first)));
    assertThat(factory.lines(new BetSlipType.Multiple(), List.of(first, second)))
        .containsExactly(new SettlementLine(0, List.of(first, second)));
  }

  @Test
  void rejectsShapeMismatchesBeforePayout() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> factory.lines(new BetSlipType.Single(), List.of(selection(), selection())));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> factory.lines(new BetSlipType.Multiple(), List.of(selection())));
  }

  private static ResolvedSelection selection() {
    return new ResolvedSelection(UUID.randomUUID(), Odds.ofDecimal("2.0000"), SettlementResult.WON);
  }
}
