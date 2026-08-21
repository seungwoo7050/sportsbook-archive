package com.sportsbook.oddsfeed.provider.real;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.oddsfeed.config.RealProperties;
import com.sportsbook.oddsfeed.provider.Sport;
import com.sportsbook.protocol.event.EventLifecycleStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class TheOddsApiProviderTest {

  private static final String EVENTS =
      """
      [{
        "id": "abc123",
        "sport_key": "soccer_epl",
        "sport_title": "EPL",
        "commence_time": "2026-06-01T18:00:00Z",
        "home_team": "Manchester United",
        "away_team": "Chelsea",
        "bookmakers": []
      }]
      """;

  @Test
  void listsConfiguredSportAndConsumesOneQuotaUnit() {
    RecordingQuota quota = new RecordingQuota();
    WebClient client =
        WebClient.builder()
            .exchangeFunction(
                request ->
                    Mono.just(
                        ClientResponse.create(HttpStatus.OK)
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .body(EVENTS)
                            .build()))
            .build();
    var properties =
        new RealProperties(
            "key",
            "https://odds.example",
            List.of("soccer_epl"),
            new RealProperties.RateLimit(5),
            500,
            60);
    var clock = Clock.fixed(Instant.parse("2026-05-28T10:00:00Z"), ZoneOffset.UTC);
    var provider = new TheOddsApiProvider(client, properties, new RateLimiter(5, clock), quota);

    var events = provider.listEvents(Sport.FOOTBALL);

    assertThat(events).hasSize(1);
    assertThat(events.get(0).competition()).isEqualTo("EPL");
    assertThat(events.get(0).status()).isEqualTo(EventLifecycleStatus.SCHEDULED);
    assertThat(quota.current()).isEqualTo(1);
  }

  @Test
  void identitiesRemainStableForTheSameProviderKeys() {
    var eventId = TheOddsApiProvider.deriveEventId("abc123");
    var repeatedEventId = TheOddsApiProvider.deriveEventId("abc123");
    var marketId = TheOddsApiProvider.deriveMarketId(eventId, "h2h");
    var selection = new TheOddsApiProvider.SelectionKey("h2h", "Chelsea");

    assertThat(eventId).isEqualTo(repeatedEventId);
    assertThat(marketId).isEqualTo(TheOddsApiProvider.deriveMarketId(eventId, "h2h"));
    assertThat(TheOddsApiProvider.deriveSelectionId(eventId, selection))
        .isEqualTo(TheOddsApiProvider.deriveSelectionId(repeatedEventId, selection));
    assertThat(TheOddsApiProvider.deriveSelectionId(eventId, selection).value())
        .isNotEqualTo(marketId.value());
  }

  @Test
  void pollingPublishesOnlyChangedSelectionPrices() {
    var body =
        new java.util.concurrent.atomic.AtomicReference<>(odds("1.85", "3.60", "4.20", "10:00"));
    WebClient client =
        WebClient.builder()
            .exchangeFunction(
                request ->
                    Mono.just(
                        ClientResponse.create(HttpStatus.OK)
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .body(body.get())
                            .build()))
            .build();
    var properties =
        new RealProperties(
            "key",
            "https://odds.example",
            List.of("soccer_epl"),
            new RealProperties.RateLimit(10),
            500,
            60);
    var clock = Clock.fixed(Instant.parse("2026-05-28T10:00:00Z"), ZoneOffset.UTC);
    var provider =
        new TheOddsApiProvider(
            client, properties, new RateLimiter(10, clock), new RecordingQuota());
    provider.pollSport(Sport.FOOTBALL);
    var eventId = TheOddsApiProvider.deriveEventId("abc123");
    var updates = new ArrayList<com.sportsbook.oddsfeed.provider.ProviderEvent.OddsUpdated>();
    var subscription =
        provider
            .streamEvents(eventId)
            .subscribe(
                event ->
                    updates.add(
                        (com.sportsbook.oddsfeed.provider.ProviderEvent.OddsUpdated) event));

    body.set(odds("1.90", "3.60", "4.00", "10:05"));
    provider.pollSport(Sport.FOOTBALL);
    provider.pollSport(Sport.FOOTBALL);
    subscription.dispose();

    assertThat(updates).hasSize(2);
    assertThat(updates)
        .extracting(update -> update.newOdds().decimal().toPlainString())
        .containsExactlyInAnyOrder("1.9000", "4.0000");
  }

  private static String odds(String home, String draw, String away, String minute) {
    return """
        [{"id":"abc123","sport_key":"soccer_epl","sport_title":"EPL",
          "commence_time":"2026-06-01T18:00:00Z","home_team":"Home","away_team":"Away",
          "bookmakers":[{"key":"book","title":"Book","last_update":"2026-05-28T%s:00Z",
          "markets":[{"key":"h2h","last_update":"2026-05-28T%s:00Z","outcomes":[
          {"name":"Home","price":%s},{"name":"Draw","price":%s},
          {"name":"Away","price":%s}]}]}]}]
        """
        .formatted(minute, minute, home, draw, away);
  }

  private static final class RecordingQuota implements QuotaCounter {
    private long used;

    @Override
    public long increment() {
      return ++used;
    }

    @Override
    public long current() {
      return used;
    }
  }
}
