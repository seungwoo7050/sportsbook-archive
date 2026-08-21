package com.sportsbook.oddsfeed.delivery;

import com.sportsbook.oddsfeed.api.EventCatalog;
import com.sportsbook.oddsfeed.cache.RedisOddsCache;
import com.sportsbook.oddsfeed.provider.EventSummary;
import com.sportsbook.oddsfeed.publisher.OddsFeedPublisher;
import com.sportsbook.protocol.event.EventLifecycleStatus;
import com.sportsbook.protocol.event.MarketStatus;
import com.sportsbook.protocol.value.EventId;
import com.sportsbook.protocol.value.MarketId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CriticalEventProcessor {

  private final CriticalEventQueue queue;
  protected final OddsFeedPublisher publisher;
  protected final RedisOddsCache cache;
  protected final EventCatalog catalog;
  private final AtomicBoolean healthy = new AtomicBoolean(true);

  public CriticalEventProcessor(
      CriticalEventQueue queue,
      OddsFeedPublisher publisher,
      RedisOddsCache cache,
      EventCatalog catalog) {
    this.queue = queue;
    this.publisher = publisher;
    this.cache = cache;
    this.catalog = catalog;
  }

  @Scheduled(fixedDelayString = "${oddsfeed.delivery.poll-interval-ms:250}")
  public void drain() {
    try {
      for (QueuedCriticalEvent queued : queue.poll()) {
        try {
          apply(queued.event());
          queue.acknowledge(queued);
          healthy.set(true);
        } catch (RuntimeException error) {
          healthy.set(false);
          break;
        }
      }
    } catch (RuntimeException error) {
      healthy.set(false);
    }
  }

  void apply(CriticalEvent event) {
    if (event.type() == CriticalEvent.Type.EVENT_LIFECYCLE) {
      applyLifecycle(event);
      return;
    }
    if (event.type() == CriticalEvent.Type.MARKET_STATUS) {
      applyMarketTransition(event);
      return;
    }
    if (event.type() == CriticalEvent.Type.MATCH_RESULT) {
      publishMatchResult(event, new EventId(event.eventId()));
      return;
    }
    throw new IllegalStateException("Unsupported critical event type: " + event.type());
  }

  private void applyMarketTransition(CriticalEvent event) {
    EventId eventId = new EventId(event.eventId());
    MarketId marketId = new MarketId(event.marketId());
    if (event.nextMarketStatus() == MarketStatus.OPEN) {
      if (cache.prepareProviderOpen(eventId, marketId) != MarketStatus.OPEN) {
        return;
      }
      publishMarketTransition(event, eventId, marketId, MarketStatus.OPEN);
      cache.storeProviderMarketStatus(eventId, marketId, MarketStatus.OPEN);
      return;
    }
    MarketStatus effective =
        cache.storeProviderMarketStatus(eventId, marketId, event.nextMarketStatus());
    publishMarketTransition(event, eventId, marketId, effective);
  }

  private void applyLifecycle(CriticalEvent event) {
    EventId eventId = new EventId(event.eventId());
    Map<UUID, MarketStatus> terminalMarkets = new LinkedHashMap<>();
    event
        .terminalMarkets()
        .forEach(
            (marketId, status) -> {
              if (status != MarketStatus.CLOSED) {
                terminalMarkets.put(marketId, status);
              }
            });
    if (isTerminal(event.lifecycleStatus())) {
      cache
          .closeEventMarkets(eventId, event.lifecycleStatus())
          .forEach(terminalMarkets::putIfAbsent);
    }
    publisher.publishEventLifecycle(
        eventId, event.lifecycleStatus(), event.scheduledStartAt(), event.occurredAt());
    cache
        .getEvent(eventId)
        .ifPresent(
            current -> {
              EventSummary updated =
                  new EventSummary(
                      current.eventId(),
                      current.sport(),
                      current.competition(),
                      current.homeTeam(),
                      current.awayTeam(),
                      current.scheduledStartAt(),
                      event.lifecycleStatus());
              cache.storeEvent(updated);
              catalog.put(updated);
            });
    terminalMarkets.forEach(
        (market, previous) ->
            publisher.publishMarketStatusChanged(
                eventId,
                new MarketId(market),
                previous,
                MarketStatus.CLOSED,
                "EVENT_" + event.lifecycleStatus(),
                event.occurredAt()));
    if (event.matchFinalStatus() != null) {
      publishMatchResult(event, eventId);
    }
  }

  private void publishMatchResult(CriticalEvent event, EventId eventId) {
    publisher.publishMatchResult(
        eventId,
        event.score(),
        event.matchFinalStatus(),
        event.resultDetail(),
        event.resultSettledAt());
  }

  private void publishMarketTransition(
      CriticalEvent event, EventId eventId, MarketId marketId, MarketStatus effectiveStatus) {
    publisher.publishMarketStatusChanged(
        eventId,
        marketId,
        event.previousMarketStatus(),
        effectiveStatus,
        event.reason(),
        event.occurredAt());
  }

  private static boolean isTerminal(EventLifecycleStatus status) {
    return status == EventLifecycleStatus.FINISHED
        || status == EventLifecycleStatus.CANCELLED
        || status == EventLifecycleStatus.POSTPONED;
  }

  public boolean isHealthy() {
    return healthy.get();
  }
}
