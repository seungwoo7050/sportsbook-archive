package com.sportsbook.oddsfeed.cache;

import com.sportsbook.protocol.value.EventId;
import com.sportsbook.protocol.value.MarketId;
import com.sportsbook.protocol.value.SelectionId;

public final class CacheKeys {

  private CacheKeys() {}

  public static String odds(EventId eventId, MarketId marketId, SelectionId selectionId) {
    return "odds:" + eventId.value() + ":" + marketId.value() + ":" + selectionId.value();
  }

  public static String event(EventId eventId) {
    return "event:" + eventId.value();
  }

  public static String market(EventId eventId, MarketId marketId) {
    return "market:" + eventId.value() + ":" + marketId.value();
  }

  public static String providerMarket(EventId eventId, MarketId marketId) {
    return "market:provider:" + eventId.value() + ":" + marketId.value();
  }

  public static String marketOverride(EventId eventId, MarketId marketId) {
    return "market:override:" + eventId.value() + ":" + marketId.value();
  }

  public static String eventMarkets(EventId eventId) {
    return "event:markets:" + eventId.value();
  }

  public static String eventTerminal(EventId eventId) {
    return "event:terminal:" + eventId.value();
  }

  public static String marketTerminal(EventId eventId, MarketId marketId) {
    return "market:terminal:" + eventId.value() + ":" + marketId.value();
  }

  public static String marketFeedHold(EventId eventId, MarketId marketId) {
    return "market:feed-hold:" + eventId.value() + ":" + marketId.value();
  }
}
