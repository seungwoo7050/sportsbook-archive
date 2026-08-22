package com.sportsbook.settlement.result;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.Odds;
import com.sportsbook.settlement.domain.Bet;
import com.sportsbook.settlement.domain.BetSelection;
import com.sportsbook.settlement.domain.EmbeddedMoney;
import com.sportsbook.settlement.domain.SlipKind;
import com.sportsbook.settlement.execution.SettlementAttempt;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BaseSettlementPlannerTest {

  @Test
  void plansMarketVoidThroughTheNormalSettledPath() {
    UUID eventId = UUID.randomUUID();
    BetSelection selection =
        new BetSelection(eventId, UUID.randomUUID(), UUID.randomUUID(), Odds.ofDecimal("2.0000"));
    selection.applyCandidate(UUID.randomUUID(), SettlementResult.VOID);
    Bet bet =
        Bet.pending(
            UUID.randomUUID(),
            UUID.randomUUID(),
            SlipKind.SINGLE,
            null,
            null,
            EmbeddedMoney.of(Money.krw(100)),
            Instant.EPOCH,
            List.of(selection),
            Instant.EPOCH);

    var draft = new BaseSettlementPlanner().plan(bet, eventId);

    assertThat(draft.action()).isEqualTo(SettlementAttempt.Action.SETTLE);
    assertThat(draft.result()).isEqualTo(SettlementResult.VOID);
    assertThat(draft.voidReason()).isNull();
    assertThat(draft.money().committed()).isEqualTo(Money.krw(100));
    assertThat(draft.money().payout()).isEqualTo(Money.krw(100));
  }
}
