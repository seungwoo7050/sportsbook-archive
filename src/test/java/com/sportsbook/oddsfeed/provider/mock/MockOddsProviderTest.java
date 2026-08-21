package com.sportsbook.oddsfeed.provider.mock;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.oddsfeed.config.MockProperties;
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

  private static MockOddsProvider newProvider(long seed) {
    MockProperties properties =
        new MockProperties(
            1.0, new MockProperties.Scenarios(false, 60), 0.45, 0.25, 0.30, seed, 500);
    return new MockOddsProvider(properties, Clock.fixed(NOW, ZoneOffset.UTC));
  }
}
