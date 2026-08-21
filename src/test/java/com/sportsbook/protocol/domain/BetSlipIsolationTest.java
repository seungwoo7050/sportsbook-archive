package com.sportsbook.protocol.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.value.BetId;
import com.sportsbook.protocol.value.EventId;
import com.sportsbook.protocol.value.MarketId;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.Odds;
import com.sportsbook.protocol.value.SelectionId;
import com.sportsbook.protocol.value.UserId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BetSlipIsolationTest {

  @Test
  void constructorCopiesMutableSelectionList() {
    ArrayList<BetSelection> selections = new ArrayList<>();
    selections.add(selection());
    BetSlip slip =
        new BetSlip(
            BetId.of(UUID.randomUUID()),
            UserId.of(UUID.randomUUID()),
            new BetSlipType.Single(),
            selections,
            Money.krw(10_000),
            BetStatus.PENDING,
            Instant.EPOCH,
            null,
            null,
            null);

    selections.add(selection());

    assertThat(slip.selections()).hasSize(1);
    assertThat(slip.selections()).isUnmodifiable();
  }

  private BetSelection selection() {
    return new BetSelection(
        EventId.of(UUID.randomUUID()),
        MarketId.of(UUID.randomUUID()),
        MarketType.MATCH_RESULT_1X2,
        SelectionId.of(UUID.randomUUID()),
        Odds.ofDecimal("1.85"));
  }
}
