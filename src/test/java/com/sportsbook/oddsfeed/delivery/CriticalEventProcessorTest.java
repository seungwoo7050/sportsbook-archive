package com.sportsbook.oddsfeed.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportsbook.oddsfeed.api.EventCatalog;
import com.sportsbook.oddsfeed.cache.RedisOddsCache;
import com.sportsbook.oddsfeed.config.CriticalDeliveryProperties;
import com.sportsbook.oddsfeed.publisher.OddsFeedPublisher;
import com.sportsbook.protocol.event.EventLifecycleStatus;
import com.sportsbook.protocol.event.MarketStatus;
import com.sportsbook.protocol.event.MatchFinalStatus;
import com.sportsbook.protocol.value.EventId;
import com.sportsbook.protocol.value.MarketId;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;

class CriticalEventProcessorTest {

  @Test
  void retainsFailedEventsAndAcknowledgesTheirRetry() {
    CriticalEvent event =
        CriticalEvent.lifecycle(
            new EventId(UUID.randomUUID()),
            EventLifecycleStatus.SCHEDULED,
            Instant.EPOCH,
            Instant.EPOCH);
    StubQueue queue = new StubQueue(event);
    RecoveringProcessor processor = new RecoveringProcessor(queue);

    processor.drain();
    assertThat(processor.isHealthy()).isFalse();
    assertThat(queue.acknowledgements).isZero();

    processor.drain();
    assertThat(processor.isHealthy()).isTrue();
    assertThat(queue.acknowledgements).isEqualTo(1);
    assertThat(queue.poll()).isEmpty();
  }

  @Test
  void restrictiveProjectionPrecedesKafkaAndStreamAcknowledgement() {
    CriticalEventQueue queue = mock(CriticalEventQueue.class);
    OddsFeedPublisher publisher = mock(OddsFeedPublisher.class);
    RedisOddsCache cache = mock(RedisOddsCache.class);
    EventId eventId = new EventId(UUID.randomUUID());
    MarketId marketId = new MarketId(UUID.randomUUID());
    CriticalEvent event =
        CriticalEvent.marketStatus(
            eventId,
            marketId,
            MarketStatus.OPEN,
            MarketStatus.SUSPENDED,
            "incident",
            Instant.EPOCH);
    QueuedCriticalEvent queued = new QueuedCriticalEvent(RecordId.of("2-0"), event, false);
    when(queue.poll()).thenReturn(List.of(queued));
    when(cache.storeProviderMarketStatus(eventId, marketId, MarketStatus.SUSPENDED))
        .thenReturn(MarketStatus.SUSPENDED);

    new CriticalEventProcessor(queue, publisher, cache, new EventCatalog()).drain();

    InOrder order = inOrder(cache, publisher, queue);
    order.verify(cache).storeProviderMarketStatus(eventId, marketId, MarketStatus.SUSPENDED);
    order
        .verify(publisher)
        .publishMarketStatusChanged(
            eventId,
            marketId,
            MarketStatus.OPEN,
            MarketStatus.SUSPENDED,
            "incident",
            Instant.EPOCH);
    order.verify(queue).acknowledge(queued);
  }

  @Test
  void providerOpenProjectsOnlyAfterKafkaAcknowledgement() {
    CriticalEventQueue queue = mock(CriticalEventQueue.class);
    OddsFeedPublisher publisher = mock(OddsFeedPublisher.class);
    RedisOddsCache cache = mock(RedisOddsCache.class);
    EventId eventId = new EventId(UUID.randomUUID());
    MarketId marketId = new MarketId(UUID.randomUUID());
    CriticalEvent event =
        CriticalEvent.marketStatus(
            eventId, marketId, MarketStatus.SUSPENDED, MarketStatus.OPEN, "resumed", Instant.EPOCH);
    QueuedCriticalEvent queued = new QueuedCriticalEvent(RecordId.of("2-1"), event, false);
    when(queue.poll()).thenReturn(List.of(queued));
    when(cache.prepareProviderOpen(eventId, marketId)).thenReturn(MarketStatus.OPEN);

    new CriticalEventProcessor(queue, publisher, cache, new EventCatalog()).drain();

    InOrder order = inOrder(publisher, cache, queue);
    order
        .verify(publisher)
        .publishMarketStatusChanged(
            eventId, marketId, MarketStatus.SUSPENDED, MarketStatus.OPEN, "resumed", Instant.EPOCH);
    order.verify(cache).storeProviderMarketStatus(eventId, marketId, MarketStatus.OPEN);
    order.verify(queue).acknowledge(queued);
  }

  @Test
  void operatorOverrideSuppressesProviderOpenPublication() {
    assertRestrictivePreviewSuppressesOpen("operator override");
  }

  @Test
  void feedHoldSuppressesProviderOpenPublication() {
    assertRestrictivePreviewSuppressesOpen("feed hold");
  }

  @Test
  void terminalStatePublishesAnEffectiveCloseForProviderSuspension() {
    assertProviderSuspensionPublishesEffectiveClose("terminal");
  }

  @Test
  void operatorClosePublishesAnEffectiveCloseForProviderSuspension() {
    assertProviderSuspensionPublishesEffectiveClose("operator close");
  }

  @Test
  void closesTerminalMarketsBeforePublishingLifecycleAndClosures() {
    CriticalEventQueue queue = mock(CriticalEventQueue.class);
    OddsFeedPublisher publisher = mock(OddsFeedPublisher.class);
    RedisOddsCache cache = mock(RedisOddsCache.class);
    EventId eventId = new EventId(UUID.randomUUID());
    MarketId embeddedMarket = new MarketId(UUID.randomUUID());
    MarketId recoveredMarket = new MarketId(UUID.randomUUID());
    CriticalEvent event =
        CriticalEvent.terminalLifecycle(
            eventId,
            EventLifecycleStatus.FINISHED,
            Instant.EPOCH,
            Instant.EPOCH,
            Map.of(embeddedMarket.value(), MarketStatus.SUSPENDED),
            null,
            null,
            Map.of(),
            null);
    QueuedCriticalEvent queued = new QueuedCriticalEvent(RecordId.of("3-0"), event, false);
    when(queue.poll()).thenReturn(List.of(queued));
    when(cache.closeEventMarkets(eventId, EventLifecycleStatus.FINISHED))
        .thenReturn(Map.of(recoveredMarket.value(), MarketStatus.OPEN));
    when(cache.getEvent(eventId)).thenReturn(Optional.empty());

    new CriticalEventProcessor(queue, publisher, cache, new EventCatalog()).drain();

    InOrder order = inOrder(cache, publisher, queue);
    order.verify(cache).closeEventMarkets(eventId, EventLifecycleStatus.FINISHED);
    order
        .verify(publisher)
        .publishEventLifecycle(
            eventId, EventLifecycleStatus.FINISHED, Instant.EPOCH, Instant.EPOCH);
    order.verify(cache).getEvent(eventId);
    order
        .verify(publisher)
        .publishMarketStatusChanged(
            eventId,
            embeddedMarket,
            MarketStatus.SUSPENDED,
            MarketStatus.CLOSED,
            "EVENT_FINISHED",
            Instant.EPOCH);
    order
        .verify(publisher)
        .publishMarketStatusChanged(
            eventId,
            recoveredMarket,
            MarketStatus.OPEN,
            MarketStatus.CLOSED,
            "EVENT_FINISHED",
            Instant.EPOCH);
    order.verify(queue).acknowledge(queued);
  }

  @Test
  void publishesDirectMatchResultBeforeAcknowledgement() {
    CriticalEventQueue queue = mock(CriticalEventQueue.class);
    OddsFeedPublisher publisher = mock(OddsFeedPublisher.class);
    EventId eventId = new EventId(UUID.randomUUID());
    CriticalEvent event =
        CriticalEvent.matchResult(
            eventId, "2-1", MatchFinalStatus.COMPLETED, Map.of("winner", "home"), Instant.EPOCH);
    QueuedCriticalEvent queued = new QueuedCriticalEvent(RecordId.of("4-0"), event, false);
    when(queue.poll()).thenReturn(List.of(queued));

    new CriticalEventProcessor(queue, publisher, mock(RedisOddsCache.class), new EventCatalog())
        .drain();

    InOrder order = inOrder(publisher, queue);
    order
        .verify(publisher)
        .publishMatchResult(
            eventId, "2-1", MatchFinalStatus.COMPLETED, Map.of("winner", "home"), Instant.EPOCH);
    order.verify(queue).acknowledge(queued);
  }

  @Test
  void publishesEmbeddedResultAfterItsTerminalLifecycle() {
    CriticalEventQueue queue = mock(CriticalEventQueue.class);
    OddsFeedPublisher publisher = mock(OddsFeedPublisher.class);
    RedisOddsCache cache = mock(RedisOddsCache.class);
    EventId eventId = new EventId(UUID.randomUUID());
    CriticalEvent event =
        CriticalEvent.terminalLifecycle(
            eventId,
            EventLifecycleStatus.FINISHED,
            Instant.EPOCH,
            Instant.EPOCH,
            Map.of(),
            "2-1",
            MatchFinalStatus.COMPLETED,
            Map.of("winner", "home"),
            Instant.EPOCH);
    QueuedCriticalEvent queued = new QueuedCriticalEvent(RecordId.of("4-1"), event, false);
    when(queue.poll()).thenReturn(List.of(queued));
    when(cache.closeEventMarkets(eventId, EventLifecycleStatus.FINISHED)).thenReturn(Map.of());
    when(cache.getEvent(eventId)).thenReturn(Optional.empty());

    new CriticalEventProcessor(queue, publisher, cache, new EventCatalog()).drain();

    InOrder order = inOrder(publisher, queue);
    order
        .verify(publisher)
        .publishEventLifecycle(
            eventId, EventLifecycleStatus.FINISHED, Instant.EPOCH, Instant.EPOCH);
    order
        .verify(publisher)
        .publishMatchResult(
            eventId, "2-1", MatchFinalStatus.COMPLETED, Map.of("winner", "home"), Instant.EPOCH);
    order.verify(queue).acknowledge(queued);
  }

  private static void assertRestrictivePreviewSuppressesOpen(String reason) {
    CriticalEventQueue queue = mock(CriticalEventQueue.class);
    OddsFeedPublisher publisher = mock(OddsFeedPublisher.class);
    RedisOddsCache cache = mock(RedisOddsCache.class);
    EventId eventId = new EventId(UUID.randomUUID());
    MarketId marketId = new MarketId(UUID.randomUUID());
    CriticalEvent event =
        CriticalEvent.marketStatus(
            eventId, marketId, MarketStatus.SUSPENDED, MarketStatus.OPEN, reason, Instant.EPOCH);
    QueuedCriticalEvent queued = new QueuedCriticalEvent(RecordId.of("2-2"), event, false);
    when(queue.poll()).thenReturn(List.of(queued));
    when(cache.prepareProviderOpen(eventId, marketId)).thenReturn(MarketStatus.SUSPENDED);

    new CriticalEventProcessor(queue, publisher, cache, new EventCatalog()).drain();

    verify(cache).prepareProviderOpen(eventId, marketId);
    verify(cache, never()).storeProviderMarketStatus(eventId, marketId, MarketStatus.OPEN);
    verifyNoInteractions(publisher);
    verify(queue).acknowledge(queued);
  }

  private static void assertProviderSuspensionPublishesEffectiveClose(String reason) {
    CriticalEventQueue queue = mock(CriticalEventQueue.class);
    OddsFeedPublisher publisher = mock(OddsFeedPublisher.class);
    RedisOddsCache cache = mock(RedisOddsCache.class);
    EventId eventId = new EventId(UUID.randomUUID());
    MarketId marketId = new MarketId(UUID.randomUUID());
    CriticalEvent event =
        CriticalEvent.marketStatus(
            eventId, marketId, MarketStatus.OPEN, MarketStatus.SUSPENDED, reason, Instant.EPOCH);
    QueuedCriticalEvent queued = new QueuedCriticalEvent(RecordId.of("2-3"), event, false);
    when(queue.poll()).thenReturn(List.of(queued));
    when(cache.storeProviderMarketStatus(eventId, marketId, MarketStatus.SUSPENDED))
        .thenReturn(MarketStatus.CLOSED);

    new CriticalEventProcessor(queue, publisher, cache, new EventCatalog()).drain();

    InOrder order = inOrder(cache, publisher, queue);
    order.verify(cache).storeProviderMarketStatus(eventId, marketId, MarketStatus.SUSPENDED);
    order
        .verify(publisher)
        .publishMarketStatusChanged(
            eventId, marketId, MarketStatus.OPEN, MarketStatus.CLOSED, reason, Instant.EPOCH);
    order.verify(queue).acknowledge(queued);
  }

  private static final class RecoveringProcessor extends CriticalEventProcessor {
    private int attempts;

    private RecoveringProcessor(CriticalEventQueue queue) {
      super(queue, null, null, null);
    }

    @Override
    void apply(CriticalEvent event) {
      if (++attempts == 1) {
        throw new IllegalStateException("temporary failure");
      }
    }
  }

  private static final class StubQueue extends CriticalEventQueue {
    private final QueuedCriticalEvent queued;
    private int acknowledgements;

    private StubQueue(CriticalEvent event) {
      super(
          new StringRedisTemplate(),
          new ObjectMapper(),
          new CriticalDeliveryProperties("stream", "group", "consumer", 1, Duration.ZERO),
          new SimpleMeterRegistry());
      queued = new QueuedCriticalEvent(RecordId.of("1-0"), event, false);
    }

    @Override
    public List<QueuedCriticalEvent> poll() {
      return acknowledgements == 0 ? List.of(queued) : List.of();
    }

    @Override
    public void acknowledge(QueuedCriticalEvent event) {
      acknowledgements++;
    }
  }
}
