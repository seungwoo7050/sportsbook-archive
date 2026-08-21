package com.sportsbook.wallet.outbox;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class OutboxPublisherConfigurationTest {

  @Test
  void requiresALeaseLongerThanKafkaBlockingDeliveryAndCompletion() {
    assertThatThrownBy(
            () -> publisher("worker-a", 1, 1, Duration.ofSeconds(15), Duration.ofMillis(1)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatCode(
            () -> publisher("worker-a", 1, 1, Duration.ofMillis(15_001), Duration.ofMillis(1)))
        .doesNotThrowAnyException();
    assertThatCode(() -> publisher("worker-a", 1, 1, Duration.ofSeconds(30), Duration.ofSeconds(1)))
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsUnusablePollingIdentityAndCapacity() {
    assertThatThrownBy(
            () -> publisher("worker-a", 1, 1, Duration.ofSeconds(30), Duration.ofNanos(1)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> publisher(" ", 1, 1, Duration.ofSeconds(30), Duration.ofMillis(1)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> publisher("x".repeat(129), 1, 1, Duration.ofSeconds(30), Duration.ofMillis(1)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> publisher("worker-a", 2, 1, Duration.ofSeconds(30), Duration.ofMillis(1)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private OutboxPublisher publisher(
      String owner, int batchSize, int inFlight, Duration lease, Duration poll) {
    OutboxPublisher publisher =
        new OutboxPublisher(null, null, null, Runnable::run, owner, batchSize, inFlight, lease);
    publisher.validatePollInterval(poll);
    return publisher;
  }
}
