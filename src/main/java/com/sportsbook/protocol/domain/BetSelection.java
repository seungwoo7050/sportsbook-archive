package com.sportsbook.protocol.domain;

import com.sportsbook.protocol.value.EventId;
import com.sportsbook.protocol.value.MarketId;
import com.sportsbook.protocol.value.Odds;
import com.sportsbook.protocol.value.SelectionId;
import java.util.Objects;

/**
 * One pick on a bet slip. {@code oddsAtPlacement} is the price the user saw at submit;
 * betting-service compares it against current odds with the slippage tolerance (ADR-0008, 3%).
 * {@code marketType} is denormalized onto the selection so settlement can resolve the right pricing
 * logic without joining back to a Market entity.
 */
public record BetSelection(
    EventId eventId,
    MarketId marketId,
    MarketType marketType,
    SelectionId selectionId,
    Odds oddsAtPlacement) {

  public BetSelection {
    Objects.requireNonNull(eventId, "eventId");
    Objects.requireNonNull(marketId, "marketId");
    Objects.requireNonNull(marketType, "marketType");
    Objects.requireNonNull(selectionId, "selectionId");
    Objects.requireNonNull(oddsAtPlacement, "oddsAtPlacement");
  }
}
