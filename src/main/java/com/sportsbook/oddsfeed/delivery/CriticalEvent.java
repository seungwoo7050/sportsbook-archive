package com.sportsbook.oddsfeed.delivery;

import com.sportsbook.protocol.event.EventLifecycleStatus;
import com.sportsbook.protocol.event.MarketStatus;
import com.sportsbook.protocol.value.EventId;
import com.sportsbook.protocol.value.MarketId;
import java.time.Instant;
import java.util.UUID;

public record CriticalEvent(
    Type type,
    UUID eventId,
    UUID marketId,
    MarketStatus previousMarketStatus,
    MarketStatus nextMarketStatus,
    String reason,
    EventLifecycleStatus lifecycleStatus,
    Instant scheduledStartAt,
    Instant occurredAt) {

  public enum Type {
    MARKET_STATUS,
    EVENT_LIFECYCLE
  }

  public static CriticalEvent marketStatus(
      EventId eventId,
      MarketId marketId,
      MarketStatus previous,
      MarketStatus next,
      String reason,
      Instant occurredAt) {
    return new CriticalEvent(
        Type.MARKET_STATUS,
        eventId.value(),
        marketId.value(),
        previous,
        next,
        reason,
        null,
        null,
        occurredAt);
  }

  public static CriticalEvent lifecycle(
      EventId eventId, EventLifecycleStatus status, Instant scheduledStartAt, Instant occurredAt) {
    return new CriticalEvent(
        Type.EVENT_LIFECYCLE,
        eventId.value(),
        null,
        null,
        null,
        null,
        status,
        scheduledStartAt,
        occurredAt);
  }
}
