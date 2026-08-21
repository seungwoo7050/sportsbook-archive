package com.sportsbook.oddsfeed.delivery;

import com.sportsbook.oddsfeed.api.EventCatalog;
import com.sportsbook.oddsfeed.cache.RedisOddsCache;
import com.sportsbook.oddsfeed.publisher.OddsFeedPublisher;
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
    throw new IllegalStateException("Unsupported critical event type: " + event.type());
  }

  public boolean isHealthy() {
    return healthy.get();
  }
}
