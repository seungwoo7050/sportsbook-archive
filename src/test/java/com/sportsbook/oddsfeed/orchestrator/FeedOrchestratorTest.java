package com.sportsbook.oddsfeed.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportsbook.oddsfeed.api.EventCatalog;
import com.sportsbook.oddsfeed.cache.RedisOddsCache;
import com.sportsbook.oddsfeed.config.CacheProperties;
import com.sportsbook.oddsfeed.provider.EventSummary;
import com.sportsbook.oddsfeed.provider.MatchOutcome;
import com.sportsbook.oddsfeed.provider.OddsProvider;
import com.sportsbook.oddsfeed.provider.ProviderEvent;
import com.sportsbook.oddsfeed.provider.Sport;
import com.sportsbook.oddsfeed.publisher.OddsFeedPublisher;
import com.sportsbook.protocol.event.EventLifecycleStatus;
import com.sportsbook.protocol.event.MarketStatus;
import com.sportsbook.protocol.value.EventId;
import com.sportsbook.protocol.value.MarketId;
import com.sportsbook.protocol.value.Odds;
import com.sportsbook.protocol.value.SelectionId;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import reactor.core.publisher.Flux;

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

  private record StubProvider(List<EventSummary> events) implements OddsProvider {
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
      return Optional.empty();
    }
  }

  private static final class RecordingCache extends RedisOddsCache {
    private final Map<EventId, EventSummary> events;
    private final List<String> order;
    private boolean held;
    private int stores;

    private RecordingCache(Map<EventId, EventSummary> events) {
      this(events, new ArrayList<>());
    }

    private RecordingCache(Map<EventId, EventSummary> events, List<String> order) {
      super(
          new StringRedisTemplate(), new ObjectMapper(), new CacheProperties(Duration.ofHours(1)));
      this.events = new HashMap<>(events);
      this.order = order;
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
  }

  private static final class RecordingPublisher extends OddsFeedPublisher {
    private final List<String> order;
    private boolean published = true;
    private boolean forceCurrentSnapshot;

    private RecordingPublisher(List<String> order) {
      super(null, null, null, null);
      this.order = order;
    }

    @Override
    public boolean isHealthy() {
      return true;
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
      return published;
    }
  }
}
