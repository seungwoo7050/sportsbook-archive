package com.sportsbook.oddsfeed.provider.mock;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.oddsfeed.config.MockProperties;
import com.sportsbook.oddsfeed.provider.ProviderEvent;
import com.sportsbook.oddsfeed.provider.Sport;
import com.sportsbook.protocol.domain.MarketType;
import com.sportsbook.protocol.event.EventLifecycleStatus;
import com.sportsbook.protocol.event.MarketStatus;
import com.sportsbook.protocol.value.MarketId;
import com.sportsbook.protocol.value.Odds;
import com.sportsbook.protocol.value.SelectionId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class MockOddsProviderTest {

  private static final Instant NOW = Instant.parse("2026-05-28T10:00:00Z");

  @Test
  void marketStartsOpenWithNamedSelections() {
    SelectionId selectionId = new SelectionId(UUID.randomUUID());
    MockOddsProvider.MockSelection selection =
        new MockOddsProvider.MockSelection(selectionId, "HOME", 0.45, Odds.ofDecimal("2.2222"));
    Map<SelectionId, MockOddsProvider.MockSelection> selections = new LinkedHashMap<>();
    selections.put(selectionId, selection);

    MockOddsProvider.MockMarket market =
        new MockOddsProvider.MockMarket(
            new MarketId(UUID.randomUUID()), MarketType.MATCH_RESULT_1X2, selections);

    assertThat(market.status).isEqualTo(MarketStatus.OPEN);
    assertThat(market.type).isEqualTo(MarketType.MATCH_RESULT_1X2);
    assertThat(market.selections.get(selectionId).name).isEqualTo("HOME");
  }

  @Test
  void seedCreatesStableScheduledCatalog() {
    MockOddsProvider first = newProvider(424242L);
    MockOddsProvider second = newProvider(424242L);
    first.seed();
    second.seed();

    assertThat(first.listEvents(Sport.FOOTBALL)).hasSize(MockOddsProvider.INITIAL_EVENT_COUNT);
    assertThat(first.listEvents(Sport.FOOTBALL))
        .extracting(summary -> summary.eventId().value())
        .containsExactlyInAnyOrderElementsOf(
            second.listEvents(Sport.FOOTBALL).stream()
                .map(summary -> summary.eventId().value())
                .toList());
    assertThat(first.listEvents(Sport.FOOTBALL))
        .allMatch(summary -> summary.status() == EventLifecycleStatus.SCHEDULED);
  }

  @Test
  void streamReplaysEventsEmittedBeforeSubscription() {
    MockOddsProvider provider = newProvider(424242L);
    provider.seed();
    var event = provider.listEvents(Sport.FOOTBALL).get(0);
    ProviderEvent update =
        new ProviderEvent.MarketStatusUpdated(
            event.eventId(),
            new MarketId(UUID.randomUUID()),
            MarketStatus.OPEN,
            MarketStatus.SUSPENDED,
            "manual review",
            NOW);

    provider.emit(event.eventId(), update);

    StepVerifier.create(provider.streamEvents(event.eventId()))
        .expectNext(update)
        .thenCancel()
        .verify();
  }

  @Test
  void tickAdvancesScheduledEventsThroughFullTime() {
    MockOddsProvider provider = newProvider(424242L);
    provider.seed();
    var event = provider.listEvents(Sport.FOOTBALL).get(0);

    provider.tick(event.scheduledStartAt());
    assertThat(provider.listEvents(Sport.FOOTBALL))
        .filteredOn(summary -> summary.eventId().equals(event.eventId()))
        .extracting(summary -> summary.status())
        .containsExactly(EventLifecycleStatus.IN_PLAY);

    provider.tick(event.scheduledStartAt().plusSeconds(90));
    assertThat(provider.listEvents(Sport.FOOTBALL))
        .filteredOn(summary -> summary.eventId().equals(event.eventId()))
        .extracting(summary -> summary.status())
        .containsExactly(EventLifecycleStatus.FINISHED);
  }

  @Test
  void oddsTicksAreDeterministicForTheConfiguredSeed() {
    MockOddsProvider first = newProvider(99L);
    MockOddsProvider second = newProvider(99L);
    first.seed();
    second.seed();
    var firstEvent = first.listEvents(Sport.FOOTBALL).get(0);
    var secondEvent =
        second.listEvents(Sport.FOOTBALL).stream()
            .filter(summary -> summary.eventId().equals(firstEvent.eventId()))
            .findFirst()
            .orElseThrow();

    first.tick(firstEvent.scheduledStartAt());
    second.tick(secondEvent.scheduledStartAt());

    var firstOdds =
        first
            .streamEvents(firstEvent.eventId())
            .ofType(ProviderEvent.OddsUpdated.class)
            .map(ProviderEvent.OddsUpdated::newOdds)
            .take(3)
            .collectList()
            .block();
    var secondOdds =
        second
            .streamEvents(secondEvent.eventId())
            .ofType(ProviderEvent.OddsUpdated.class)
            .map(ProviderEvent.OddsUpdated::newOdds)
            .take(3)
            .collectList()
            .block();

    assertThat(firstOdds).containsExactlyElementsOf(secondOdds);
  }

  private static MockOddsProvider newProvider(long seed) {
    MockProperties properties =
        new MockProperties(
            1.0, new MockProperties.Scenarios(false, 60), 0.45, 0.25, 0.30, seed, 500);
    return new MockOddsProvider(properties, Clock.fixed(NOW, ZoneOffset.UTC));
  }
}
