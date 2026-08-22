package com.sportsbook.settlement.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.value.Odds;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BetSelectionTest {

  @Test
  void preservesIdentifiersOddsAndAggregateOrder() {
    UUID eventId = UUID.randomUUID();
    UUID marketId = UUID.randomUUID();
    UUID selectionId = UUID.randomUUID();
    BetSelection selection =
        new BetSelection(eventId, marketId, selectionId, Odds.ofDecimal("2.12500"));

    Bet owner =
        Bet.pending(
            UUID.randomUUID(),
            UUID.randomUUID(),
            SlipKind.SINGLE,
            null,
            null,
            new EmbeddedMoney(100, com.sportsbook.protocol.value.Currency.KRW),
            java.time.Instant.EPOCH,
            java.util.List.of(selection),
            java.time.Instant.EPOCH);

    assertThat(selection.selectionRowId().version()).isEqualTo(7);
    assertThat(selection.eventId()).isEqualTo(eventId);
    assertThat(selection.marketId()).isEqualTo(marketId);
    assertThat(selection.selectionId()).isEqualTo(selectionId);
    assertThat(selection.odds()).isEqualTo(Odds.ofDecimal("2.1250"));
    assertThat(selection.legIndex()).isZero();
    assertThat(owner.selections()).containsExactly(selection);
  }
}
