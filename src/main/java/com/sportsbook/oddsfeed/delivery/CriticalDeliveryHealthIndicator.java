package com.sportsbook.oddsfeed.delivery;

import com.sportsbook.oddsfeed.publisher.OddsFeedPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class CriticalDeliveryHealthIndicator implements HealthIndicator {

  private final CriticalEventQueue queue;
  private final OddsFeedPublisher publisher;
  private final CriticalEventProcessor processor;
  private final OperatorActionQueue operatorQueue;
  private final OperatorActionProcessor operatorProcessor;

  @Autowired
  public CriticalDeliveryHealthIndicator(
      CriticalEventQueue queue,
      OddsFeedPublisher publisher,
      CriticalEventProcessor processor,
      OperatorActionQueue operatorQueue,
      OperatorActionProcessor operatorProcessor) {
    this.queue = queue;
    this.publisher = publisher;
    this.processor = processor;
    this.operatorQueue = operatorQueue;
    this.operatorProcessor = operatorProcessor;
  }

  CriticalDeliveryHealthIndicator(
      CriticalEventQueue queue, OddsFeedPublisher publisher, CriticalEventProcessor processor) {
    this(queue, publisher, processor, null, null);
  }

  @Override
  public Health health() {
    boolean operatorAvailable =
        operatorQueue == null || operatorQueue.isHealthy() && operatorProcessor.isHealthy();
    boolean available =
        queue.isHealthy() && publisher.isHealthy() && processor.isHealthy() && operatorAvailable;
    Health.Builder health = available ? Health.up() : Health.down();
    return health
        .withDetail("redisStream", queue.isHealthy() ? "UP" : "DOWN")
        .withDetail("kafkaPublisher", publisher.isHealthy() ? "UP" : "DOWN")
        .withDetail("criticalProcessor", processor.isHealthy() ? "UP" : "DOWN")
        .withDetail("pendingRecords", queue.pendingCount())
        .withDetail("operatorDelivery", operatorAvailable ? "UP" : "DOWN")
        .withDetail(
            "operatorPendingRecords", operatorQueue == null ? 0 : operatorQueue.pendingCount())
        .build();
  }
}
