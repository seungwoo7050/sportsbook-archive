package com.sportsbook.protocol.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.sportsbook.protocol.value.EventId;
import com.sportsbook.protocol.value.MarketId;
import com.sportsbook.protocol.value.Odds;
import com.sportsbook.protocol.value.SelectionId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BetSelectionTest {

  private final EventId eventId = EventId.of(UUID.randomUUID());
  private final MarketId marketId = MarketId.of(UUID.randomUUID());
  private final SelectionId selectionId = SelectionId.of(UUID.randomUUID());
  private final Odds odds = Odds.ofDecimal("1.85");

  @Test
  void selectionPreservesPlacementSnapshot() {
    BetSelection selection =
        new BetSelection(eventId, marketId, MarketType.MATCH_RESULT_1X2, selectionId, odds);
    assertThat(selection.eventId()).isEqualTo(eventId);
    assertThat(selection.marketId()).isEqualTo(marketId);
    assertThat(selection.marketType()).isEqualTo(MarketType.MATCH_RESULT_1X2);
    assertThat(selection.selectionId()).isEqualTo(selectionId);
    assertThat(selection.oddsAtPlacement()).isEqualTo(odds);
  }

  @Test
  void requiredFieldsRejectNulls() {
    assertThatNullPointerException()
        .isThrownBy(
            () -> new BetSelection(null, marketId, MarketType.MATCH_RESULT_1X2, selectionId, odds));
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new BetSelection(
                    eventId, marketId, MarketType.MATCH_RESULT_1X2, selectionId, null));
  }
}
