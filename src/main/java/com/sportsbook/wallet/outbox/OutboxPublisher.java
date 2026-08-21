package com.sportsbook.wallet.outbox;

import com.sportsbook.wallet.config.KafkaProducerConfig;
import com.sportsbook.wallet.persistence.OutboxDeliveryRepository;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "wallet.outbox.scheduling-enabled", havingValue = "true")
public class OutboxPublisher {

  private static final Duration COMPLETION_MARGIN = Duration.ofSeconds(5);
  private static final int MAXIMUM_OWNER_LENGTH = 128;

  private final OutboxDeliveryRepository delivery;
  private final OutboxDispatcher dispatcher;
  private final OutboxRetryPolicy retryPolicy;
  private final Executor completionExecutor;
  private final String owner;
  private final int batchSize;
  private final Duration leaseDuration;
  private final Semaphore inFlight;

  public OutboxPublisher(
      OutboxDeliveryRepository delivery,
      OutboxDispatcher dispatcher,
      OutboxRetryPolicy retryPolicy,
      @Qualifier("applicationTaskExecutor") Executor completionExecutor,
      @Value("${wallet.outbox.owner:${HOSTNAME:wallet-service}-${random.uuid}}") String owner,
      @Value("${wallet.outbox.batch-size:20}") int batchSize,
      @Value("${wallet.outbox.max-in-flight:100}") int maximumInFlight,
      @Value("${wallet.outbox.lease-duration:PT30S}") Duration leaseDuration) {
    if (batchSize < 1 || maximumInFlight < 1 || batchSize > maximumInFlight) {
      throw new IllegalArgumentException("invalid outbox delivery limits");
    }
    if (owner == null || owner.isBlank() || owner.length() > MAXIMUM_OWNER_LENGTH) {
      throw new IllegalArgumentException("invalid outbox owner");
    }
    Duration minimumLease =
        KafkaProducerConfig.MAX_BLOCK_TIME
            .plus(KafkaProducerConfig.DELIVERY_TIMEOUT)
            .plus(COMPLETION_MARGIN);
    if (leaseDuration == null || leaseDuration.compareTo(minimumLease) <= 0) {
      throw new IllegalArgumentException("outbox lease must exceed delivery timeout and margin");
    }
    this.delivery = delivery;
    this.dispatcher = dispatcher;
    this.retryPolicy = retryPolicy;
    this.completionExecutor = completionExecutor;
    this.owner = owner;
    this.batchSize = batchSize;
    this.leaseDuration = leaseDuration;
    this.inFlight = new Semaphore(maximumInFlight);
  }

  @Autowired
  void validatePollInterval(@Value("${wallet.outbox.poll-interval:PT1S}") Duration pollInterval) {
    if (pollInterval == null || pollInterval.toMillis() < 1L) {
      throw new IllegalArgumentException("outbox poll interval must be at least one millisecond");
    }
  }

  @Scheduled(fixedDelayString = "${wallet.outbox.poll-interval:PT1S}")
  public synchronized void poll() {
    int capacity = Math.min(batchSize, inFlight.availablePermits());
    if (capacity == 0) {
      return;
    }
    List<LeasedOutboxMessage> messages = delivery.claim(owner, capacity, leaseDuration);
    messages.forEach(this::dispatch);
  }

  private void dispatch(LeasedOutboxMessage message) {
    if (!inFlight.tryAcquire()) {
      throw new IllegalStateException("claimed beyond in-flight capacity");
    }
    try {
      dispatcher
          .dispatch(message)
          .whenCompleteAsync((ignored, failure) -> complete(message, failure), completionExecutor);
    } catch (RuntimeException failure) {
      complete(message, failure);
    }
  }

  private void complete(LeasedOutboxMessage message, Throwable failure) {
    try {
      if (failure == null) {
        delivery.markPublished(message.lease());
      } else {
        delivery.releaseForRetry(
            message.lease(),
            retryPolicy.delayForAttempt(message.attemptCount()),
            retryPolicy.describe(failure));
      }
    } finally {
      inFlight.release();
    }
  }
}
