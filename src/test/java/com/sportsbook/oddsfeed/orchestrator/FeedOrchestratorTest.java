package com.sportsbook.oddsfeed.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportsbook.oddsfeed.api.EventCatalog;
import com.sportsbook.oddsfeed.cache.RedisOddsCache;
import com.sportsbook.oddsfeed.config.CacheProperties;
import com.sportsbook.oddsfeed.config.CriticalDeliveryProperties;
import com.sportsbook.oddsfeed.delivery.CriticalEvent;
import com.sportsbook.oddsfeed.delivery.CriticalEventQueue;
import com.sportsbook.oddsfeed.provider.EventSummary;
import com.sportsbook.oddsfeed.provider.MatchOutcome;
import com.sportsbook.oddsfeed.provider.OddsProvider;
import com.sportsbook.oddsfeed.provider.ProviderEvent;
import com.sportsbook.oddsfeed.provider.Sport;
import com.sportsbook.oddsfeed.publisher.KafkaPublishException;
import com.sportsbook.oddsfeed.publisher.OddsFeedPublisher;
import com.sportsbook.protocol.event.EventLifecycleStatus;
import com.sportsbook.protocol.event.MarketStatus;
import com.sportsbook.protocol.event.MatchFinalStatus;
import com.sportsbook.protocol.value.EventId;
import com.sportsbook.protocol.value.MarketId;
import com.sportsbook.protocol.value.Odds;
import com.sportsbook.protocol.value.SelectionId;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

class FeedOrchestratorTest {

  @Test
  void seedsNewEventsAndPreservesCachedLifecycleState() {
    EventSummary fresh = event(UUID.randomUUID(), EventLifecycleStatus.SCHEDULED);
    EventSummary providerCopy = event(UUID.randomUUID(), EventLifecycleStatus.SCHEDULED);
    EventSummary cached = event(providerCopy.eventId().value(), EventLifecycleStatus.FINISHED);
    RecordingCache cache = new RecordingCache(Map.of(cached.eventId(), cached));
    EventCatalog catalog = new EventCatalog();
    FeedOrchestrator orchestrator =
        new FeedOrchestrator(new StubProvider(List.of(fresh, providerCopy)), cache, catalog);

    orchestrator.refresh();
    orchestrator.refresh();

    assertThat(catalog.get(fresh.eventId())).contains(fresh);
    assertThat(catalog.get(cached.eventId())).contains(cached);
    assertThat(cache.stores).isEqualTo(1);
  }

  @Test
  void projectsOddsOnlyAfterPublisherAcknowledgement() {
    List<String> order = new ArrayList<>();
    RecordingCache cache = new RecordingCache(Map.of(), order);
    RecordingPublisher publisher = new RecordingPublisher(order);
    FeedOrchestrator orchestrator =
        new FeedOrchestrator(new StubProvider(List.of()), cache, publisher, new EventCatalog());
    EventId eventId = new EventId(UUID.randomUUID());
    ProviderEvent odds =
        new ProviderEvent.OddsUpdated(
            eventId,
            new MarketId(UUID.randomUUID()),
            new SelectionId(UUID.randomUUID()),
            Odds.ofDecimal("2.00"),
            Odds.ofDecimal("2.10"),
            Instant.EPOCH);

    cache.held = true;
    publisher.published = false;
    orchestrator.dispatch(eventId, odds);
    assertThat(order).containsExactly("publish");

    publisher.published = true;
    orchestrator.dispatch(eventId, odds);
    assertThat(order).containsExactly("publish", "publish", "cache");
    assertThat(publisher.forceCurrentSnapshot).isTrue();
  }

  @Test
  void holdsLatestOddsWhileTheBrokerIsUnavailable() {
    assertOutageOrder(new RecordingPublisher(new ArrayList<>(), false, false), "hold");
    assertOutageOrder(new RecordingPublisher(new ArrayList<>(), true, true), "publish", "hold");
  }

  @Test
  void enqueuesBeforeRestrictingMarketsAndDefersOpening() {
    assertMarketOrder(MarketStatus.SUSPENDED, false, "enqueue", "cache");
    assertMarketOrder(MarketStatus.OPEN, false, "enqueue");
    assertMarketOrder(MarketStatus.SUSPENDED, true, "enqueue");
  }

  @Test
  void snapshotsTerminalMarketsBeforeEnqueueAndClosure() {
    List<String> order = new ArrayList<>();
    EventId eventId = new EventId(UUID.randomUUID());
    MarketId open = new MarketId(UUID.randomUUID());
    MarketId closed = new MarketId(UUID.randomUUID());
    MatchOutcome outcome =
        new MatchOutcome(
            eventId, "2-1", MatchFinalStatus.COMPLETED, Map.of("winner", "home"), Instant.EPOCH);
    RecordingCache cache =
        new RecordingCache(
            Map.of(), order, Map.of(open, MarketStatus.OPEN, closed, MarketStatus.CLOSED));
    RecordingQueue queue = new RecordingQueue(order, false);
    FeedOrchestrator orchestrator =
        new FeedOrchestrator(
            new StubProvider(List.of(), Optional.of(outcome)),
            cache,
            new RecordingPublisher(order),
            new EventCatalog(),
            queue);

    orchestrator.dispatch(
        eventId,
        new ProviderEvent.LifecycleUpdated(
            eventId, EventLifecycleStatus.FINISHED, Instant.EPOCH, Instant.EPOCH));

    assertThat(order).containsExactly("snapshot", "enqueue", "close");
    assertThat(queue.enqueued.terminalMarkets()).containsOnlyKeys(open.value());
    assertThat(queue.enqueued.score()).isEqualTo("2-1");

    List<String> failedOrder = new ArrayList<>();
    FeedOrchestrator failing =
        new FeedOrchestrator(
            new StubProvider(List.of()),
            new RecordingCache(Map.of(), failedOrder, Map.of(open, MarketStatus.OPEN)),
            new RecordingPublisher(failedOrder),
            new EventCatalog(),
            new RecordingQueue(failedOrder, true));
    assertThatThrownBy(
            () ->
                failing.dispatch(
                    eventId,
                    new ProviderEvent.LifecycleUpdated(
                        eventId, EventLifecycleStatus.CANCELLED, Instant.EPOCH, Instant.EPOCH)))
        .isInstanceOf(IllegalStateException.class);
    assertThat(failedOrder).containsExactly("snapshot", "enqueue");
  }

  @Test
  void keepsOneActiveSubscriptionAndReplacesCompletedStreams() {
    List<String> order = new ArrayList<>();
    EventSummary summary = event(UUID.randomUUID(), EventLifecycleStatus.SCHEDULED);
    SubscriptionProvider provider = new SubscriptionProvider(summary);
    FeedOrchestrator orchestrator =
        new FeedOrchestrator(
            provider,
            new RecordingCache(Map.of(), order),
            new RecordingPublisher(order),
            new EventCatalog(),
            null);

    orchestrator.refresh();
    orchestrator.refresh();
    assertThat(provider.subscriptions).hasValue(1);

    provider.events.tryEmitNext(
        new ProviderEvent.OddsUpdated(
            summary.eventId(),
            new MarketId(UUID.randomUUID()),
            new SelectionId(UUID.randomUUID()),
            Odds.ofDecimal("2.00"),
            Odds.ofDecimal("2.10"),
            Instant.EPOCH));
    assertThat(order).containsExactly("publish", "cache");

    provider.events.tryEmitComplete();
    orchestrator.refresh();
    assertThat(provider.subscriptions).hasValue(2);
    orchestrator.stop();
  }

  @Test
  void retriesFailedStreamsAfterABoundedBackoff() throws InterruptedException {
    List<String> order = new ArrayList<>();
    EventSummary summary = event(UUID.randomUUID(), EventLifecycleStatus.SCHEDULED);
    ProviderEvent lifecycle =
        new ProviderEvent.LifecycleUpdated(
            summary.eventId(),
            EventLifecycleStatus.CANCELLED,
            summary.scheduledStartAt(),
            Instant.EPOCH);
    RetryProvider provider = new RetryProvider(summary, lifecycle);
    RetryQueue queue = new RetryQueue(order);
    FeedOrchestrator orchestrator =
        new FeedOrchestrator(
            provider,
            new RecordingCache(Map.of(), order, Map.of()),
            new RecordingPublisher(order),
            new EventCatalog(),
            queue);
    long startedAt = System.nanoTime();

    orchestrator.refresh();

    assertThat(queue.delivered.await(3, TimeUnit.SECONDS)).isTrue();
    long elapsedMillis = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    assertThat(elapsedMillis).isBetween(500L, 3000L);
    assertThat(provider.subscriptions).hasValue(2);
    assertThat(order).containsExactly("snapshot", "enqueue", "snapshot", "enqueue", "close");
    orchestrator.stop();
  }

  private static void assertMarketOrder(
      MarketStatus next, boolean failEnqueue, String... expected) {
    List<String> order = new ArrayList<>();
    EventId eventId = new EventId(UUID.randomUUID());
    FeedOrchestrator orchestrator =
        new FeedOrchestrator(
            new StubProvider(List.of()),
            new RecordingCache(Map.of(), order),
            new RecordingPublisher(order),
            new EventCatalog(),
            new RecordingQueue(order, failEnqueue));
    ProviderEvent event =
        new ProviderEvent.MarketStatusUpdated(
            eventId,
            new MarketId(UUID.randomUUID()),
            MarketStatus.OPEN,
            next,
            "provider update",
            Instant.EPOCH);

    if (failEnqueue) {
      assertThatThrownBy(() -> orchestrator.dispatch(eventId, event))
          .isInstanceOf(IllegalStateException.class);
    } else {
      orchestrator.dispatch(eventId, event);
    }
    assertThat(order).containsExactly(expected);
  }

  private static void assertOutageOrder(RecordingPublisher publisher, String... expected) {
    RecordingCache cache = new RecordingCache(Map.of(), publisher.order);
    FeedOrchestrator orchestrator =
        new FeedOrchestrator(new StubProvider(List.of()), cache, publisher, new EventCatalog());
    EventId eventId = new EventId(UUID.randomUUID());
    orchestrator.dispatch(
        eventId,
        new ProviderEvent.OddsUpdated(
            eventId,
            new MarketId(UUID.randomUUID()),
            new SelectionId(UUID.randomUUID()),
            Odds.ofDecimal("2.00"),
            Odds.ofDecimal("2.10"),
            Instant.EPOCH));
    assertThat(publisher.order).containsExactly(expected);
  }

  private static EventSummary event(UUID id, EventLifecycleStatus status) {
    return new EventSummary(
        new EventId(id),
        Sport.FOOTBALL,
        "Premier League",
        "Home",
        "Away",
        Instant.parse("2026-06-01T18:00:00Z"),
        status);
  }

  private record StubProvider(List<EventSummary> events, Optional<MatchOutcome> result)
      implements OddsProvider {
    private StubProvider(List<EventSummary> events) {
      this(events, Optional.empty());
    }

    @Override
    public List<EventSummary> listEvents(Sport sport) {
      return sport == Sport.FOOTBALL ? events : List.of();
    }

    @Override
    public Flux<ProviderEvent> streamEvents(EventId eventId) {
      return Flux.empty();
    }

    @Override
    public Optional<MatchOutcome> getMatchResult(EventId eventId) {
      return result;
    }
  }

  private static final class SubscriptionProvider implements OddsProvider {
    private final EventSummary summary;
    private final AtomicInteger subscriptions = new AtomicInteger();
    private final Sinks.Many<ProviderEvent> events = Sinks.many().replay().limit(1);

    private SubscriptionProvider(EventSummary summary) {
      this.summary = summary;
    }

    @Override
    public List<EventSummary> listEvents(Sport sport) {
      return sport == summary.sport() ? List.of(summary) : List.of();
    }

    @Override
    public Flux<ProviderEvent> streamEvents(EventId eventId) {
      return Flux.defer(
          () -> {
            subscriptions.incrementAndGet();
            return events.asFlux();
          });
    }

    @Override
    public Optional<MatchOutcome> getMatchResult(EventId eventId) {
      return Optional.empty();
    }
  }

  private static final class RetryProvider implements OddsProvider {
    private final EventSummary summary;
    private final ProviderEvent event;
    private final AtomicInteger subscriptions = new AtomicInteger();

    private RetryProvider(EventSummary summary, ProviderEvent event) {
      this.summary = summary;
      this.event = event;
    }

    @Override
    public List<EventSummary> listEvents(Sport sport) {
      return sport == summary.sport() ? List.of(summary) : List.of();
    }

    @Override
    public Flux<ProviderEvent> streamEvents(EventId eventId) {
      return Flux.defer(
          () -> {
            subscriptions.incrementAndGet();
            return Flux.just(event);
          });
    }

    @Override
    public Optional<MatchOutcome> getMatchResult(EventId eventId) {
      return Optional.empty();
    }
  }

  private static final class RecordingCache extends RedisOddsCache {
    private final Map<EventId, EventSummary> events;
    private final List<String> order;
    private boolean held;
    private final Map<MarketId, MarketStatus> markets;
    private int stores;

    private RecordingCache(Map<EventId, EventSummary> events) {
      this(events, new ArrayList<>());
    }

    private RecordingCache(Map<EventId, EventSummary> events, List<String> order) {
      this(events, order, Map.of());
    }

    private RecordingCache(
        Map<EventId, EventSummary> events,
        List<String> order,
        Map<MarketId, MarketStatus> markets) {
      super(
          new StringRedisTemplate(), new ObjectMapper(), new CacheProperties(Duration.ofHours(1)));
      this.events = new HashMap<>(events);
      this.order = order;
      this.markets = markets;
    }

    @Override
    public Optional<EventSummary> getEvent(EventId eventId) {
      return Optional.ofNullable(events.get(eventId));
    }

    @Override
    public void storeEvent(EventSummary summary) {
      stores++;
      events.put(summary.eventId(), summary);
    }

    @Override
    public MarketStatus projectLatestOdds(
        EventId eventId,
        MarketId marketId,
        SelectionId selectionId,
        Odds odds,
        Instant observedAt) {
      order.add("cache");
      return MarketStatus.SUSPENDED;
    }

    @Override
    public boolean isFeedHeld(EventId eventId, MarketId marketId) {
      return held;
    }

    @Override
    public MarketStatus holdLatestOdds(
        EventId eventId,
        MarketId marketId,
        SelectionId selectionId,
        Odds odds,
        Instant observedAt) {
      order.add("hold");
      return MarketStatus.SUSPENDED;
    }

    @Override
    public MarketStatus storeProviderMarketStatus(
        EventId eventId, MarketId marketId, MarketStatus status) {
      order.add("cache");
      return status;
    }

    @Override
    public Map<MarketId, MarketStatus> getRegisteredMarkets(EventId eventId) {
      order.add("snapshot");
      return markets;
    }

    @Override
    public Map<UUID, MarketStatus> closeEventMarkets(
        EventId eventId, EventLifecycleStatus terminalStatus) {
      order.add("close");
      return Map.of();
    }
  }

  private static final class RecordingQueue extends CriticalEventQueue {
    private final List<String> order;
    private final boolean fail;
    private CriticalEvent enqueued;

    private RecordingQueue(List<String> order, boolean fail) {
      super(
          new StringRedisTemplate(),
          new ObjectMapper(),
          new CriticalDeliveryProperties("stream", "group", "consumer", 1, Duration.ZERO),
          new SimpleMeterRegistry());
      this.order = order;
      this.fail = fail;
    }

    @Override
    public RecordId enqueue(CriticalEvent event) {
      order.add("enqueue");
      enqueued = event;
      if (fail) {
        throw new IllegalStateException("Redis unavailable");
      }
      return RecordId.of("1-0");
    }
  }

  private static final class RetryQueue extends CriticalEventQueue {
    private final List<String> order;
    private final CountDownLatch delivered = new CountDownLatch(1);
    private int attempts;

    private RetryQueue(List<String> order) {
      super(
          new StringRedisTemplate(),
          new ObjectMapper(),
          new CriticalDeliveryProperties("stream", "group", "consumer", 1, Duration.ZERO),
          new SimpleMeterRegistry());
      this.order = order;
    }

    @Override
    public RecordId enqueue(CriticalEvent event) {
      order.add("enqueue");
      if (++attempts == 1) {
        throw new IllegalStateException("Redis unavailable");
      }
      delivered.countDown();
      return RecordId.of("2-0");
    }
  }

  private static final class RecordingPublisher extends OddsFeedPublisher {
    private final List<String> order;
    private boolean published = true;
    private boolean forceCurrentSnapshot;
    private final boolean healthy;
    private final boolean fail;

    private RecordingPublisher(List<String> order) {
      this(order, true, false);
    }

    private RecordingPublisher(List<String> order, boolean healthy, boolean fail) {
      super(null, null, null, null);
      this.order = order;
      this.healthy = healthy;
      this.fail = fail;
    }

    @Override
    public boolean isHealthy() {
      return healthy;
    }

    @Override
    public boolean publishOddsChanged(
        EventId eventId,
        MarketId marketId,
        SelectionId selectionId,
        Odds previous,
        Odds next,
        Instant changedAt,
        boolean forceCurrentSnapshot) {
      order.add("publish");
      this.forceCurrentSnapshot = forceCurrentSnapshot;
      if (fail) {
        throw new KafkaPublishException("broker unavailable", new IllegalStateException());
      }
      return published;
    }
  }
}
