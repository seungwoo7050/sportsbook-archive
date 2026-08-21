package com.sportsbook.oddsfeed.delivery;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sportsbook.oddsfeed.publisher.OddsFeedPublisher;
import com.sportsbook.protocol.event.MarketStatus;
import com.sportsbook.protocol.value.EventId;
import com.sportsbook.protocol.value.MarketId;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.data.redis.connection.stream.RecordId;

class OperatorActionProcessorTest {

  @Test
  void publishesResolvedReopenBeforeCompletionAndStreamCleanup() {
    OperatorActionQueue queue = mock(OperatorActionQueue.class);
    OddsFeedPublisher publisher = mock(OddsFeedPublisher.class);
    QueuedOperatorMarketAction queued = queuedAction();
    when(queue.poll()).thenReturn(List.of(queued));
    when(queue.deliveryDecision(queued.action()))
        .thenReturn(decision(OperatorDeliveryDecision.Outcome.PUBLISH, MarketStatus.OPEN));
    when(queue.complete(queued.action())).thenReturn(OperatorActionQueue.Completion.APPLIED);

    new OperatorActionProcessor(queue, publisher, new SimpleMeterRegistry()).drain();

    InOrder order = inOrder(queue, publisher);
    order.verify(queue).poll();
    order.verify(queue).deliveryDecision(queued.action());
    order
        .verify(publisher)
        .publishMarketStatusChanged(
            queued.action().eventId(),
            queued.action().marketId(),
            queued.action().previousStatus(),
            MarketStatus.OPEN,
            queued.action().reason(),
            queued.action().occurredAt());
    order.verify(queue).complete(queued.action());
    order.verify(queue).cleanup(queued);
  }

  @Test
  void terminalOrSupersededReopenCompletesWithoutPublishing() {
    OperatorActionQueue queue = mock(OperatorActionQueue.class);
    OddsFeedPublisher publisher = mock(OddsFeedPublisher.class);
    QueuedOperatorMarketAction queued = queuedAction();
    when(queue.poll()).thenReturn(List.of(queued));
    when(queue.deliveryDecision(queued.action()))
        .thenReturn(decision(OperatorDeliveryDecision.Outcome.SKIP, null));
    when(queue.complete(queued.action())).thenReturn(OperatorActionQueue.Completion.APPLIED);

    new OperatorActionProcessor(queue, publisher, new SimpleMeterRegistry()).drain();

    InOrder order = inOrder(queue, publisher);
    order.verify(queue).deliveryDecision(queued.action());
    order.verify(queue).complete(queued.action());
    order.verify(queue).cleanup(queued);
    verify(publisher, never())
        .publishMarketStatusChanged(
            queued.action().eventId(),
            queued.action().marketId(),
            queued.action().previousStatus(),
            queued.action().announcedStatus(),
            queued.action().reason(),
            queued.action().occurredAt());
  }

  @Test
  void publishesReopenUsingTheLatestRestrictiveProjection() {
    OperatorActionQueue queue = mock(OperatorActionQueue.class);
    OddsFeedPublisher publisher = mock(OddsFeedPublisher.class);
    QueuedOperatorMarketAction queued = queuedAction();
    when(queue.poll()).thenReturn(List.of(queued));
    when(queue.deliveryDecision(queued.action()))
        .thenReturn(decision(OperatorDeliveryDecision.Outcome.PUBLISH, MarketStatus.SUSPENDED));
    when(queue.complete(queued.action())).thenReturn(OperatorActionQueue.Completion.APPLIED);

    new OperatorActionProcessor(queue, publisher, new SimpleMeterRegistry()).drain();

    verify(publisher)
        .publishMarketStatusChanged(
            queued.action().eventId(),
            queued.action().marketId(),
            queued.action().previousStatus(),
            MarketStatus.SUSPENDED,
            queued.action().reason(),
            queued.action().occurredAt());
    verify(queue).cleanup(queued);
  }

  private static OperatorDeliveryDecision decision(
      OperatorDeliveryDecision.Outcome outcome, MarketStatus announcedStatus) {
    return new OperatorDeliveryDecision(outcome, announcedStatus);
  }

  private static QueuedOperatorMarketAction queuedAction() {
    OperatorMarketAction action =
        new OperatorMarketAction(
            UUID.randomUUID(),
            new EventId(UUID.randomUUID()),
            new MarketId(UUID.randomUUID()),
            MarketStatus.SUSPENDED,
            MarketStatus.OPEN,
            MarketStatus.OPEN,
            "incident",
            1,
            0,
            Instant.parse("2026-08-21T05:00:00Z"));
    return new QueuedOperatorMarketAction(RecordId.of("1-0"), action, false);
  }
}
