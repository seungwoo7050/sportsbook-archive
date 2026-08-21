package com.sportsbook.oddsfeed.orchestrator;

import com.sportsbook.oddsfeed.api.EventCatalog;
import com.sportsbook.oddsfeed.cache.RedisOddsCache;
import com.sportsbook.oddsfeed.delivery.CriticalEvent;
import com.sportsbook.oddsfeed.delivery.CriticalEventQueue;
import com.sportsbook.oddsfeed.provider.EventSummary;
import com.sportsbook.oddsfeed.provider.OddsProvider;
import com.sportsbook.oddsfeed.provider.ProviderEvent;
import com.sportsbook.oddsfeed.provider.Sport;
import com.sportsbook.oddsfeed.publisher.KafkaPublishException;
import com.sportsbook.oddsfeed.publisher.OddsFeedPublisher;
import com.sportsbook.protocol.event.MarketStatus;
import com.sportsbook.protocol.value.EventId;
import jakarta.annotation.PostConstruct;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class FeedOrchestrator {

  private final OddsProvider provider;
  private final RedisOddsCache cache;
  private final OddsFeedPublisher publisher;
  private final EventCatalog catalog;
  private final CriticalEventQueue criticalQueue;

  public FeedOrchestrator(OddsProvider provider, RedisOddsCache cache, EventCatalog catalog) {
    this(provider, cache, null, catalog, null);
  }

  public FeedOrchestrator(
      OddsProvider provider,
      RedisOddsCache cache,
      OddsFeedPublisher publisher,
      EventCatalog catalog) {
    this(provider, cache, publisher, catalog, null);
  }

  @Autowired
  public FeedOrchestrator(
      OddsProvider provider,
      RedisOddsCache cache,
      OddsFeedPublisher publisher,
      EventCatalog catalog,
      CriticalEventQueue criticalQueue) {
    this.provider = provider;
    this.cache = cache;
    this.publisher = publisher;
    this.catalog = catalog;
    this.criticalQueue = criticalQueue;
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

  void dispatch(EventId eventId, ProviderEvent event) {
    if (event instanceof ProviderEvent.OddsUpdated odds) {
      handleOdds(odds);
    } else if (event instanceof ProviderEvent.MarketStatusUpdated status) {
      handleMarketStatus(status);
    }
  }

  private void handleOdds(ProviderEvent.OddsUpdated odds) {
    if (!publisher.isHealthy()) {
      cache.holdLatestOdds(
          odds.eventId(), odds.marketId(), odds.selectionId(), odds.newOdds(), odds.occurredAt());
      return;
    }
    try {
      boolean held = cache.isFeedHeld(odds.eventId(), odds.marketId());
      boolean published =
          publisher.publishOddsChanged(
              odds.eventId(),
              odds.marketId(),
              odds.selectionId(),
              odds.previousOdds(),
              odds.newOdds(),
              odds.occurredAt(),
              held);
      if (held && !published) {
        return;
      }
    } catch (KafkaPublishException error) {
      cache.holdLatestOdds(
          odds.eventId(), odds.marketId(), odds.selectionId(), odds.newOdds(), odds.occurredAt());
      return;
    }
    cache.projectLatestOdds(
        odds.eventId(), odds.marketId(), odds.selectionId(), odds.newOdds(), odds.occurredAt());
  }

  private void handleMarketStatus(ProviderEvent.MarketStatusUpdated status) {
    criticalQueue.enqueue(
        CriticalEvent.marketStatus(
            status.eventId(),
            status.marketId(),
            status.previousStatus(),
            status.newStatus(),
            status.reason(),
            status.occurredAt()));
    if (status.newStatus() != MarketStatus.OPEN) {
      cache.storeProviderMarketStatus(status.eventId(), status.marketId(), status.newStatus());
    }
  }
}
