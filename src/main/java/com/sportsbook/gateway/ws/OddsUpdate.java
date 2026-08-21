package com.sportsbook.gateway.ws;

import com.sportsbook.protocol.event.OddsChanged;
import java.time.Instant;

/** Public odds projection delivered on an event-specific STOMP topic. */
public record OddsUpdate(
    String eventId,
    String marketId,
    String selectionId,
    String previousOdds,
    String newOdds,
    Instant changedAt) {

  static OddsUpdate from(OddsChanged event) {
    return new OddsUpdate(
        event.getEventId(),
        event.getMarketId(),
        event.getSelectionId(),
        event.getPreviousOdds(),
        event.getNewOdds(),
        event.getChangedAt());
  }
}
