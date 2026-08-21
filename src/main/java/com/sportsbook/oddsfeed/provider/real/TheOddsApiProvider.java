package com.sportsbook.oddsfeed.provider.real;

import com.sportsbook.oddsfeed.config.RealProperties;
import com.sportsbook.oddsfeed.provider.EventSummary;
import com.sportsbook.oddsfeed.provider.MatchOutcome;
import com.sportsbook.oddsfeed.provider.OddsProvider;
import com.sportsbook.oddsfeed.provider.ProviderEvent;
import com.sportsbook.oddsfeed.provider.Sport;
import com.sportsbook.protocol.event.EventLifecycleStatus;
import com.sportsbook.protocol.value.EventId;
import com.sportsbook.protocol.value.MarketId;
import com.sportsbook.protocol.value.Odds;
import com.sportsbook.protocol.value.SelectionId;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Component
@Profile("real")
public class TheOddsApiProvider implements OddsProvider {

  private static final Duration FETCH_TIMEOUT = Duration.ofSeconds(10);

  private final WebClient client;
  private final RealProperties properties;
  private final RateLimiter rateLimiter;
  private final QuotaCounter quotaCounter;
  private final Map<EventId, Sinks.Many<ProviderEvent>> streams = new ConcurrentHashMap<>();
  private final Map<EventId, Map<SelectionKey, Odds>> lastSeen = new ConcurrentHashMap<>();

  public TheOddsApiProvider(
      WebClient theOddsWebClient,
      RealProperties properties,
      RateLimiter rateLimiter,
      QuotaCounter quotaCounter) {
    this.client = theOddsWebClient;
    this.properties = properties;
    this.rateLimiter = rateLimiter;
    this.quotaCounter = quotaCounter;
  }

  @Override
  public List<EventSummary> listEvents(Sport sport) {
    String sportKey = sportKey(sport);
    if (sportKey == null) {
      return List.of();
    }
    return fetch(sportKey).stream().map(event -> toSummary(event, sport)).toList();
  }

  @Override
  public Flux<ProviderEvent> streamEvents(EventId eventId) {
    return streams
        .computeIfAbsent(eventId, ignored -> Sinks.many().multicast().onBackpressureBuffer())
        .asFlux();
  }

  @Override
  public Optional<MatchOutcome> getMatchResult(EventId eventId) {
    return Optional.empty();
  }

  @Scheduled(
      fixedRateString = "${oddsfeed.real.poll-interval-seconds:60}",
      timeUnit = java.util.concurrent.TimeUnit.SECONDS)
  void scheduledPoll() {
    for (Sport sport : Sport.values()) {
      pollSport(sport);
    }
  }

  void pollSport(Sport sport) {
    String sportKey = sportKey(sport);
    if (sportKey == null) {
      return;
    }
    for (TheOddsApiDtos.Event event : fetch(sportKey)) {
      EventId eventId = deriveEventId(event.id());
      Map<SelectionKey, Odds> next = prices(event);
      Map<SelectionKey, Odds> previous = lastSeen.put(eventId, next);
      if (previous != null) {
        emitChanges(eventId, previous, next, observedAt(event));
      }
    }
  }

  private List<TheOddsApiDtos.Event> fetch(String sportKey) {
    if (!rateLimiter.tryAcquire() || quotaCounter.increment() > properties.monthlyQuota()) {
      return List.of();
    }
    TheOddsApiDtos.Event[] response =
        client
            .get()
            .uri(
                builder ->
                    builder
                        .path("/sports/{key}/odds")
                        .queryParam("apiKey", properties.apiKey())
                        .queryParam("regions", "uk")
                        .queryParam("markets", "h2h")
                        .queryParam("oddsFormat", "decimal")
                        .build(sportKey))
            .retrieve()
            .bodyToMono(TheOddsApiDtos.Event[].class)
            .block(FETCH_TIMEOUT);
    return response == null ? List.of() : List.of(response);
  }

  private String sportKey(Sport sport) {
    String preferred =
        switch (sport) {
          case FOOTBALL -> "soccer_epl";
          case BASKETBALL -> "basketball_nba";
        };
    return properties.sportKeys().contains(preferred) ? preferred : null;
  }

  private EventSummary toSummary(TheOddsApiDtos.Event event, Sport sport) {
    return new EventSummary(
        deriveEventId(event.id()),
        sport,
        event.sportTitle(),
        event.homeTeam(),
        event.awayTeam(),
        event.commenceTime(),
        EventLifecycleStatus.SCHEDULED);
  }

  private Map<SelectionKey, Odds> prices(TheOddsApiDtos.Event event) {
    Map<SelectionKey, Odds> prices = new LinkedHashMap<>();
    if (event.bookmakers() == null || event.bookmakers().isEmpty()) {
      return prices;
    }
    for (TheOddsApiDtos.Market market : event.bookmakers().get(0).markets()) {
      if ("h2h".equals(market.key())) {
        for (TheOddsApiDtos.Outcome outcome : market.outcomes()) {
          prices.put(
              new SelectionKey(market.key(), outcome.name()), Odds.ofDecimal(outcome.price()));
        }
      }
    }
    return prices;
  }

  private Instant observedAt(TheOddsApiDtos.Event event) {
    return event.bookmakers().isEmpty()
        ? event.commenceTime()
        : event.bookmakers().get(0).lastUpdate();
  }

  private void emitChanges(
      EventId eventId,
      Map<SelectionKey, Odds> previous,
      Map<SelectionKey, Odds> next,
      Instant observedAt) {
    Sinks.Many<ProviderEvent> sink = streams.get(eventId);
    if (sink == null) {
      return;
    }
    next.forEach(
        (key, odds) -> {
          Odds prior = previous.get(key);
          if (!odds.equals(prior)) {
            sink.tryEmitNext(
                new ProviderEvent.OddsUpdated(
                    eventId,
                    deriveMarketId(eventId, key.marketKey()),
                    deriveSelectionId(eventId, key),
                    prior == null ? odds : prior,
                    odds,
                    observedAt));
          }
        });
  }

  static EventId deriveEventId(String upstreamId) {
    return new EventId(named(upstreamId));
  }

  static MarketId deriveMarketId(EventId eventId, String marketKey) {
    return new MarketId(named(eventId.value() + ":" + marketKey));
  }

  static SelectionId deriveSelectionId(EventId eventId, SelectionKey selection) {
    return new SelectionId(
        named(eventId.value() + ":" + selection.marketKey() + ":" + selection.outcomeName()));
  }

  private static UUID named(String value) {
    return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
  }

  record SelectionKey(String marketKey, String outcomeName) {}
}
