package com.sportsbook.oddsfeed.delivery;

import com.sportsbook.oddsfeed.publisher.OddsFeedPublisher;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class CriticalDeliveryHealthIndicator implements HealthIndicator {

  private final CriticalEventQueue queue;
  private final OddsFeedPublisher publisher;
  private final CriticalEventProcessor processor;

  public CriticalDeliveryHealthIndicator(
      CriticalEventQueue queue, OddsFeedPublisher publisher, CriticalEventProcessor processor) {
    this.queue = queue;
    this.publisher = publisher;
    this.processor = processor;
  }

  @Override
  public Health health() {
    boolean available = queue.isHealthy() && publisher.isHealthy() && processor.isHealthy();
    Health.Builder health = available ? Health.up() : Health.down();
    return health
        .withDetail("redisStream", queue.isHealthy() ? "UP" : "DOWN")
        .withDetail("kafkaPublisher", publisher.isHealthy() ? "UP" : "DOWN")
        .withDetail("criticalProcessor", processor.isHealthy() ? "UP" : "DOWN")
        .withDetail("pendingRecords", queue.pendingCount())
        .build();
  }
}
