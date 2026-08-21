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
import com.sportsbook.protocol.event.EventLifecycleStatus;
import com.sportsbook.protocol.value.EventId;
import java.time.Duration;
import java.time.Instant;
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
    private int stores;

    private RecordingCache(Map<EventId, EventSummary> events) {
      super(
          new StringRedisTemplate(), new ObjectMapper(), new CacheProperties(Duration.ofHours(1)));
      this.events = new HashMap<>(events);
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
  }
}
