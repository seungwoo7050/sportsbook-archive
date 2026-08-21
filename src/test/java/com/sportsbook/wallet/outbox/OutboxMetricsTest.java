package com.sportsbook.wallet.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sportsbook.wallet.persistence.OutboxDeliveryRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class OutboxMetricsTest {

  @Test
  void recordsPublisherRetriesOnlyWhenTheirFenceWins() {
    OutboxDeliveryRepository delivery = mock(OutboxDeliveryRepository.class);
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    OutboxMetrics metrics = new OutboxMetrics(registry);
    var first = message("0198ca71-8000-7000-8000-0000000000b0", 1L, false);
    var second = message("0198ca71-8000-7000-8000-0000000000b1", 2L, true);
    when(delivery.claim("worker-a", 2, Duration.ofSeconds(30))).thenReturn(List.of(first, second));
    when(delivery.releaseForRetry(any(), any(), anyString())).thenReturn(true, false);
    OutboxPublisher publisher =
        new OutboxPublisher(
            delivery,
            ignored -> CompletableFuture.failedFuture(new IllegalStateException("broker down")),
            new OutboxRetryPolicy(Duration.ofSeconds(1), Duration.ofSeconds(60)),
            Runnable::run,
            "worker-a",
            2,
            2,
            Duration.ofSeconds(30));
    publisher.useMetrics(metrics);

    publisher.poll();

    verify(delivery, times(2)).releaseForRetry(any(), any(), anyString());
    assertThat(count(registry, "wallet.outbox.claimed")).isEqualTo(2);
    assertThat(count(registry, "wallet.outbox.lease.takeovers")).isEqualTo(1);
    assertThat(count(registry, "wallet.outbox.retried")).isEqualTo(1);
    assertThat(count(registry, "wallet.outbox.fenced.completion")).isEqualTo(1);
  }

  private double count(SimpleMeterRegistry registry, String name) {
    return registry.get(name).counter().count();
  }

  private LeasedOutboxMessage message(String eventId, long sequence, boolean takeover) {
    Instant created = Instant.parse("2026-08-21T00:00:00Z");
    return new LeasedOutboxMessage(
        new OutboxLease(UUID.fromString(eventId), "worker-a", 1, created.plusSeconds(30)),
        "wallet.debited.v1",
        eventId,
        "WalletDebited",
        new byte[] {1},
        sequence,
        takeover,
        1,
        created);
  }
}
