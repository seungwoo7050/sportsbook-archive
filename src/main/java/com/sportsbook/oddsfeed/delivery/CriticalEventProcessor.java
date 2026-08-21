package com.sportsbook.oddsfeed.delivery;

import com.sportsbook.oddsfeed.api.EventCatalog;
import com.sportsbook.oddsfeed.cache.RedisOddsCache;
import com.sportsbook.oddsfeed.publisher.OddsFeedPublisher;
import com.sportsbook.protocol.event.MarketStatus;
import com.sportsbook.protocol.value.EventId;
import com.sportsbook.protocol.value.MarketId;
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
    if (event.type() != CriticalEvent.Type.MARKET_STATUS) {
      throw new IllegalStateException("Unsupported critical event type: " + event.type());
    }
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

  public boolean isHealthy() {
    return healthy.get();
  }
}
