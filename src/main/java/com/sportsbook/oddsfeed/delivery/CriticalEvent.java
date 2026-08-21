package com.sportsbook.oddsfeed.delivery;

import com.sportsbook.protocol.event.EventLifecycleStatus;
import com.sportsbook.protocol.event.MarketStatus;
import com.sportsbook.protocol.event.MatchFinalStatus;
import com.sportsbook.protocol.value.EventId;
import com.sportsbook.protocol.value.MarketId;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
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
    Map<UUID, MarketStatus> terminalMarkets,
    String score,
    MatchFinalStatus matchFinalStatus,
    Map<String, String> resultDetail,
    Instant resultSettledAt,
    Instant occurredAt) {

  public enum Type {
    MARKET_STATUS,
    EVENT_LIFECYCLE,
    MATCH_RESULT
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
        Map.of(),
        null,
        null,
        Map.of(),
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
        Map.of(),
        null,
        null,
        Map.of(),
        null,
        occurredAt);
  }

  public static CriticalEvent terminalLifecycle(
      EventId eventId,
      EventLifecycleStatus status,
      Instant scheduledStartAt,
      Instant occurredAt,
      Map<UUID, MarketStatus> terminalMarkets,
      String score,
      MatchFinalStatus finalStatus,
      Map<String, String> resultDetail,
      Instant settledAt) {
    return new CriticalEvent(
        Type.EVENT_LIFECYCLE,
        eventId.value(),
        null,
        null,
        null,
        null,
        status,
        scheduledStartAt,
        Collections.unmodifiableMap(new LinkedHashMap<>(terminalMarkets)),
        score,
        finalStatus,
        Map.copyOf(resultDetail),
        settledAt,
        occurredAt);
  }

  public static CriticalEvent matchResult(
      EventId eventId,
      String score,
      MatchFinalStatus finalStatus,
      Map<String, String> resultDetail,
      Instant settledAt) {
    return new CriticalEvent(
        Type.MATCH_RESULT,
        eventId.value(),
        null,
        null,
        null,
        null,
        null,
        null,
        Map.of(),
        score,
        finalStatus,
        Map.copyOf(resultDetail),
        settledAt,
        settledAt);
  }
}
