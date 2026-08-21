package com.sportsbook.oddsfeed.orchestrator;

import com.sportsbook.oddsfeed.api.EventCatalog;
import com.sportsbook.oddsfeed.cache.RedisOddsCache;
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
import com.sportsbook.protocol.value.EventId;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.util.retry.Retry;

@Component
public class FeedOrchestrator {

  private static final Duration RECONNECT_INITIAL_BACKOFF = Duration.ofSeconds(1);
  private static final Duration RECONNECT_MAX_BACKOFF = Duration.ofSeconds(30);
  private static final double RECONNECT_JITTER = 0.2;

  private final OddsProvider provider;
  private final RedisOddsCache cache;
  private final OddsFeedPublisher publisher;
  private final EventCatalog catalog;
  private final CriticalEventQueue criticalQueue;
  private final Map<EventId, Disposable> subscriptions = new ConcurrentHashMap<>();

  public FeedOrchestrator(OddsProvider provider, RedisOddsCache cache, EventCatalog catalog) {
    this(provider, cache, null, catalog, null);
  }

  public FeedOrchestrator(
      OddsProvider provider,
      RedisOddsCache cache,
      OddsFeedPublisher publisher,
      EventCatalog catalog) {
    this(provider, cache, publisher, catalog, null);
  }

  @Autowired
  public FeedOrchestrator(
      OddsProvider provider,
      RedisOddsCache cache,
      OddsFeedPublisher publisher,
      EventCatalog catalog,
      CriticalEventQueue criticalQueue) {
    this.provider = provider;
    this.cache = cache;
    this.publisher = publisher;
    this.catalog = catalog;
    this.criticalQueue = criticalQueue;
  }

  @PostConstruct
  void start() {
    refresh();
  }

  @PreDestroy
  void stop() {
    subscriptions.values().forEach(Disposable::dispose);
    subscriptions.clear();
  }

  @Scheduled(
      fixedRateString = "${oddsfeed.orchestrator.refresh-interval-seconds:30}",
      timeUnit = TimeUnit.SECONDS)
  void refresh() {
    for (Sport sport : Sport.values()) {
      for (EventSummary summary : provider.listEvents(sport)) {
        seedProjection(summary);
        ensureSubscribed(summary.eventId());
      }
    }
  }

  private void seedProjection(EventSummary providerSummary) {
    if (catalog.get(providerSummary.eventId()).isPresent()) {
      return;
    }
    Optional<EventSummary> cached = cache.getEvent(providerSummary.eventId());
    EventSummary initial = cached.orElse(providerSummary);
    if (catalog.putIfAbsent(initial) && cached.isEmpty()) {
      cache.storeEvent(initial);
    }
  }

  private void ensureSubscribed(EventId eventId) {
    Disposable existing = subscriptions.get(eventId);
    if (existing != null && !existing.isDisposed()) {
      return;
    }
    if (existing != null) {
      subscriptions.remove(eventId, existing);
    }
    AtomicBoolean terminated = new AtomicBoolean();
    AtomicReference<Disposable> self = new AtomicReference<>();
    Flux<ProviderEvent> stream =
        Flux.defer(() -> provider.streamEvents(eventId))
            .doOnNext(event -> dispatch(eventId, event))
            .retryWhen(
                Retry.backoff(Long.MAX_VALUE, RECONNECT_INITIAL_BACKOFF)
                    .maxBackoff(RECONNECT_MAX_BACKOFF)
                    .jitter(RECONNECT_JITTER))
            .doFinally(
                signal -> {
                  terminated.set(true);
                  Disposable registered = self.get();
                  if (registered != null) {
                    subscriptions.remove(eventId, registered);
                  }
                });
    Disposable created = stream.subscribe(ignored -> {}, error -> {});
    self.set(created);
    if (created.isDisposed() || terminated.get()) {
      return;
    }
    Disposable raced = subscriptions.putIfAbsent(eventId, created);
    if (raced != null) {
      created.dispose();
    } else if (terminated.get() || created.isDisposed()) {
      subscriptions.remove(eventId, created);
    }
  }

  void dispatch(EventId eventId, ProviderEvent event) {
    if (event instanceof ProviderEvent.OddsUpdated odds) {
      handleOdds(odds);
    } else if (event instanceof ProviderEvent.MarketStatusUpdated status) {
      handleMarketStatus(status);
    } else if (event instanceof ProviderEvent.LifecycleUpdated lifecycle) {
      handleLifecycle(lifecycle);
    }
  }

  private void handleOdds(ProviderEvent.OddsUpdated odds) {
    if (!publisher.isHealthy()) {
      cache.holdLatestOdds(
          odds.eventId(), odds.marketId(), odds.selectionId(), odds.newOdds(), odds.occurredAt());
      return;
    }
    try {
      boolean held = cache.isFeedHeld(odds.eventId(), odds.marketId());
      boolean published =
          publisher.publishOddsChanged(
              odds.eventId(),
              odds.marketId(),
              odds.selectionId(),
              odds.previousOdds(),
              odds.newOdds(),
              odds.occurredAt(),
              held);
      if (held && !published) {
        return;
      }
    } catch (KafkaPublishException error) {
      cache.holdLatestOdds(
          odds.eventId(), odds.marketId(), odds.selectionId(), odds.newOdds(), odds.occurredAt());
      return;
    }
    cache.projectLatestOdds(
        odds.eventId(), odds.marketId(), odds.selectionId(), odds.newOdds(), odds.occurredAt());
  }

  private void handleMarketStatus(ProviderEvent.MarketStatusUpdated status) {
    criticalQueue.enqueue(
        CriticalEvent.marketStatus(
            status.eventId(),
            status.marketId(),
            status.previousStatus(),
            status.newStatus(),
            status.reason(),
            status.occurredAt()));
    if (status.newStatus() != MarketStatus.OPEN) {
      cache.storeProviderMarketStatus(status.eventId(), status.marketId(), status.newStatus());
    }
  }

  private void handleLifecycle(ProviderEvent.LifecycleUpdated lifecycle) {
    if (!isTerminal(lifecycle.status())) {
      criticalQueue.enqueue(
          CriticalEvent.lifecycle(
              lifecycle.eventId(),
              lifecycle.status(),
              lifecycle.scheduledStartAt(),
              lifecycle.occurredAt()));
      return;
    }
    Map<UUID, MarketStatus> terminalMarkets = new LinkedHashMap<>();
    cache
        .getRegisteredMarkets(lifecycle.eventId())
        .forEach(
            (marketId, status) -> {
              if (status != MarketStatus.CLOSED) {
                terminalMarkets.put(marketId.value(), status);
              }
            });
    Optional<MatchOutcome> outcome =
        lifecycle.status() == EventLifecycleStatus.FINISHED
            ? provider.getMatchResult(lifecycle.eventId())
            : Optional.empty();
    MatchOutcome result = outcome.orElse(null);
    criticalQueue.enqueue(
        CriticalEvent.terminalLifecycle(
            lifecycle.eventId(),
            lifecycle.status(),
            lifecycle.scheduledStartAt(),
            lifecycle.occurredAt(),
            terminalMarkets,
            result == null ? null : result.score(),
            result == null ? null : result.finalStatus(),
            result == null ? Map.of() : result.detail(),
            result == null ? null : result.settledAt()));
    cache.closeEventMarkets(lifecycle.eventId(), lifecycle.status());
  }

  private static boolean isTerminal(EventLifecycleStatus status) {
    return status == EventLifecycleStatus.FINISHED
        || status == EventLifecycleStatus.CANCELLED
        || status == EventLifecycleStatus.POSTPONED;
  }
}
