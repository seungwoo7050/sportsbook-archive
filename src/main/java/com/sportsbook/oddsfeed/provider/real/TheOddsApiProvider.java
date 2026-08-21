package com.sportsbook.oddsfeed.provider.real;

import com.sportsbook.oddsfeed.config.RealProperties;
import com.sportsbook.oddsfeed.provider.EventSummary;
import com.sportsbook.oddsfeed.provider.Sport;
import com.sportsbook.protocol.event.EventLifecycleStatus;
import com.sportsbook.protocol.value.EventId;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
@Profile("real")
public class TheOddsApiProvider {

  private static final Duration FETCH_TIMEOUT = Duration.ofSeconds(10);

  private final WebClient client;
  private final RealProperties properties;
  private final RateLimiter rateLimiter;
  private final QuotaCounter quotaCounter;

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

  public List<EventSummary> listEvents(Sport sport) {
    String sportKey = sportKey(sport);
    if (sportKey == null) {
      return List.of();
    }
    return fetch(sportKey).stream().map(event -> toSummary(event, sport)).toList();
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
    EventId id = new EventId(UUID.nameUUIDFromBytes(event.id().getBytes(StandardCharsets.UTF_8)));
    return new EventSummary(
        id,
        sport,
        event.sportTitle(),
        event.homeTeam(),
        event.awayTeam(),
        event.commenceTime(),
        EventLifecycleStatus.SCHEDULED);
  }
}
