package com.sportsbook.oddsfeed.delivery;

import com.sportsbook.oddsfeed.publisher.OddsFeedPublisher;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Publishes durable operator actions and advances each market with an acknowledged CAS. */
@Component
public class OperatorActionProcessor {

  private static final Logger log = LoggerFactory.getLogger(OperatorActionProcessor.class);

  private final OperatorActionQueue queue;
  private final OddsFeedPublisher publisher;
  private final Counter processed;
  private final Counter failures;
  private final AtomicBoolean healthy = new AtomicBoolean(true);

  public OperatorActionProcessor(
      OperatorActionQueue queue, OddsFeedPublisher publisher, MeterRegistry meterRegistry) {
    this.queue = queue;
    this.publisher = publisher;
    this.processed = meterRegistry.counter("oddsfeed.operator.action.processed");
    this.failures = meterRegistry.counter("oddsfeed.operator.action.processing.failure");
  }

  @Scheduled(fixedDelayString = "${oddsfeed.operator.delivery.poll-interval-ms:250}")
  void drain() {
    var queuedActions = queue.poll();
    if (queuedActions.isEmpty() && queue.pendingCount() == 0) {
      healthy.set(true);
    }
    for (QueuedOperatorMarketAction queued : queuedActions) {
      OperatorMarketAction action = queued.action();
      try {
        OperatorDeliveryDecision decision = queue.deliveryDecision(action);
        if (decision.outcome() == OperatorDeliveryDecision.Outcome.COMPLETED) {
          queue.cleanup(queued);
          processed.increment();
          healthy.set(true);
          continue;
        }
        if (decision.outcome() == OperatorDeliveryDecision.Outcome.BLOCKED) {
          break;
        }

        if (decision.outcome() == OperatorDeliveryDecision.Outcome.PUBLISH) {
          publisher.publishMarketStatusChanged(
              action.eventId(),
              action.marketId(),
              action.previousStatus(),
              decision.announcedStatus(),
              action.reason(),
              action.occurredAt());
        }
        OperatorActionQueue.Completion completion = queue.complete(action);
        if (completion == OperatorActionQueue.Completion.BLOCKED) {
          break;
        }
        queue.cleanup(queued);
        processed.increment();
        healthy.set(true);
      } catch (RuntimeException exception) {
        failures.increment();
        healthy.set(false);
        log.warn(
            "Operator action {} remains pending after delivery failure: {}",
            action.actionId(),
            exception.toString());
        break;
      }
    }
  }

  public boolean isHealthy() {
    return healthy.get();
  }
}
