package com.sportsbook.wallet.outbox;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sportsbook.wallet.persistence.OutboxDeliveryRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;

class OutboxPublisherTest {

  @Test
  void waitsForTheBrokerAcknowledgementBeforePublishing() {
    OutboxDeliveryRepository delivery = mock(OutboxDeliveryRepository.class);
    ControlledDispatcher dispatcher = new ControlledDispatcher();
    LeasedOutboxMessage message = message();
    when(delivery.claim("worker-a", 1, Duration.ofSeconds(30L))).thenReturn(List.of(message));

    publisher(delivery, dispatcher).poll();

    verify(delivery, never()).markPublished(any());
    dispatcher.result.complete(null);
    verify(delivery).markPublished(message.lease());
  }

  @Test
  void retriesAfterAnAsynchronousBrokerFailure() {
    OutboxDeliveryRepository delivery = mock(OutboxDeliveryRepository.class);
    ControlledDispatcher dispatcher = new ControlledDispatcher();
    LeasedOutboxMessage message = message();
    when(delivery.claim("worker-a", 1, Duration.ofSeconds(30L))).thenReturn(List.of(message));

    publisher(delivery, dispatcher).poll();
    dispatcher.result.completeExceptionally(new IllegalStateException("broker unavailable"));

    verify(delivery, never()).markPublished(any());
    verify(delivery)
        .releaseForRetry(
            eq(message.lease()),
            eq(Duration.ofSeconds(1L)),
            eq("IllegalStateException: broker unavailable"));
  }

  private static OutboxPublisher publisher(
      OutboxDeliveryRepository delivery, OutboxDispatcher dispatcher) {
    return new OutboxPublisher(
        delivery,
        dispatcher,
        new OutboxRetryPolicy(Duration.ofSeconds(1L), Duration.ofSeconds(60L)),
        Runnable::run,
        "worker-a",
        1,
        1,
        Duration.ofSeconds(30L));
  }

  private static LeasedOutboxMessage message() {
    Instant created = Instant.parse("2026-08-21T00:00:00Z");
    UUID eventId = UUID.fromString("0198ca71-8000-7000-8000-0000000000b0");
    return new LeasedOutboxMessage(
        new OutboxLease(eventId, "worker-a", 1L, created.plusSeconds(30L)),
        "wallet.debited.v1",
        "user-1",
        "WalletDebited",
        new byte[] {1},
        1L,
        false,
        1,
        created);
  }

  private static final class ControlledDispatcher implements OutboxDispatcher {
    private final CompletableFuture<Void> result = new CompletableFuture<>();

    @Override
    public CompletionStage<Void> dispatch(LeasedOutboxMessage message) {
      return result;
    }
  }
}
