package com.sportsbook.settlement.resolver;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.protocol.value.Odds;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ResolvedSelectionTest {

  @Test
  void distinguishesWinningAndStakeReturningLegs() {
    ResolvedSelection won = selection(SettlementResult.WON);
    ResolvedSelection push = selection(SettlementResult.PUSH);
    ResolvedSelection voided = selection(SettlementResult.VOID);

    assertThat(won.wins()).isTrue();
    assertThat(won.returnsStake()).isFalse();
    assertThat(push.returnsStake()).isTrue();
    assertThat(voided.returnsStake()).isTrue();
    assertThat(selection(SettlementResult.LOST).returnsStake()).isFalse();
  }

  private static ResolvedSelection selection(SettlementResult result) {
    return new ResolvedSelection(UUID.randomUUID(), Odds.ofDecimal("2.0000"), result);
  }
}
