package com.sportsbook.oddsfeed.provider.mock;

import com.sportsbook.oddsfeed.provider.ProviderEvent;
import com.sportsbook.protocol.event.EventLifecycleStatus;
import com.sportsbook.protocol.event.MarketStatus;
import java.time.Instant;
import java.util.Random;

public final class SuddenMarketSuspend implements MockScenario {

  private static final String REASON = "simulated in-play pause";

  @Override
  public String id() {
    return "SuddenMarketSuspend";
  }

  @Override
  public boolean canApply(MockOddsProvider.MockEvent event, Instant now) {
    if (event.status != EventLifecycleStatus.IN_PLAY
        && event.status != EventLifecycleStatus.SCHEDULED) {
      return false;
    }
    return event.markets.values().stream().anyMatch(market -> market.status == MarketStatus.OPEN);
  }

  @Override
  public void apply(
      MockOddsProvider.MockEvent event, Instant now, Random rng, MockOddsProvider provider) {
    MockOddsProvider.MockMarket target =
        event.markets.values().stream()
            .filter(market -> market.status == MarketStatus.OPEN)
            .findFirst()
            .orElseThrow();
    MarketStatus previous = target.status;
    target.status = MarketStatus.SUSPENDED;
    provider.emit(
        event.summary.eventId(),
        new ProviderEvent.MarketStatusUpdated(
            event.summary.eventId(),
            target.marketId,
            previous,
            MarketStatus.SUSPENDED,
            REASON,
            now));
  }
}
