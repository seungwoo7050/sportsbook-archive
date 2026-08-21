package com.sportsbook.oddsfeed.orchestrator;

import com.sportsbook.oddsfeed.api.EventCatalog;
import com.sportsbook.oddsfeed.cache.RedisOddsCache;
import com.sportsbook.oddsfeed.provider.EventSummary;
import com.sportsbook.oddsfeed.provider.OddsProvider;
import com.sportsbook.oddsfeed.provider.Sport;
import jakarta.annotation.PostConstruct;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class FeedOrchestrator {

  private final OddsProvider provider;
  private final RedisOddsCache cache;
  private final EventCatalog catalog;

  public FeedOrchestrator(OddsProvider provider, RedisOddsCache cache, EventCatalog catalog) {
    this.provider = provider;
    this.cache = cache;
    this.catalog = catalog;
  }

  @PostConstruct
  void start() {
    refresh();
  }

  @Scheduled(
      fixedRateString = "${oddsfeed.orchestrator.refresh-interval-seconds:30}",
      timeUnit = TimeUnit.SECONDS)
  void refresh() {
    for (Sport sport : Sport.values()) {
      for (EventSummary summary : provider.listEvents(sport)) {
        seedProjection(summary);
      }
    }
  }

  private void seedProjection(EventSummary providerSummary) {
    if (catalog.get(providerSummary.eventId()).isPresent()) {
      return;
    }
    Optional<EventSummary> cached = cache.getEvent(providerSummary.eventId());
    EventSummary initial = cached.orElse(providerSummary);
    if (catalog.putIfAbsent(initial) && cached.isEmpty()) {
      cache.storeEvent(initial);
    }
  }
}
