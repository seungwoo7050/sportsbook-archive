package com.sportsbook.oddsfeed.delivery;

import com.sportsbook.protocol.event.MarketStatus;
import com.sportsbook.protocol.value.EventId;
import com.sportsbook.protocol.value.MarketId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** An immutable market transition accepted from an internal operator. */
public record OperatorMarketAction(
    UUID actionId,
    EventId eventId,
    MarketId marketId,
    MarketStatus previousStatus,
    MarketStatus announcedStatus,
    MarketStatus requestedStatus,
    String reason,
    long sequence,
    long predecessor,
    Instant occurredAt) {

  public OperatorMarketAction {
    Objects.requireNonNull(actionId, "actionId");
    Objects.requireNonNull(eventId, "eventId");
    Objects.requireNonNull(marketId, "marketId");
    Objects.requireNonNull(previousStatus, "previousStatus");
    Objects.requireNonNull(announcedStatus, "announcedStatus");
    Objects.requireNonNull(requestedStatus, "requestedStatus");
    Objects.requireNonNull(reason, "reason");
    Objects.requireNonNull(occurredAt, "occurredAt");
    if (sequence <= 0 || predecessor != sequence - 1) {
      throw new IllegalArgumentException("Invalid operator action sequence");
    }
  }
}
