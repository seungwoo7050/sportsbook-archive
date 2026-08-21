package com.sportsbook.oddsfeed.provider.mock;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.oddsfeed.config.MockProperties;
import com.sportsbook.oddsfeed.provider.EventSummary;
import com.sportsbook.oddsfeed.provider.ProviderEvent;
import com.sportsbook.protocol.event.EventLifecycleStatus;
import com.sportsbook.protocol.event.MarketStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MockScenarioTest {

  private static final Instant NOW = Instant.parse("2026-05-28T10:00:00Z");
  private static final MockProperties PROPERTIES =
      new MockProperties(1.0, new MockProperties.Scenarios(false, 60), 0.45, 0.25, 0.30, 42L, 500);

  private MockOddsProvider provider;
  private MockOddsProvider.MockEvent event;

  @BeforeEach
  void setUp() {
    provider = new MockOddsProvider(PROPERTIES, Clock.fixed(NOW, ZoneOffset.UTC));
    provider.seed();
    event = provider.activeEvents().iterator().next();
  }

  @Test
  void postponedScenarioTransitionsScheduledEvent() {
    EventSummary before = event.summary;

    new MatchPostponed().apply(event, NOW, new Random(0), provider);

    assertThat(event.status).isEqualTo(EventLifecycleStatus.POSTPONED);
    assertThat(event.summary.eventId()).isEqualTo(before.eventId());
  }

  @Test
  void postponedScenarioRejectsInPlayEvent() {
    provider.tick(event.kickoffAt);

    assertThat(new MatchPostponed().canApply(event, event.kickoffAt)).isFalse();
  }

  @Test
  void postponedEventStopsAdvancing() {
    new MatchPostponed().apply(event, NOW, new Random(0), provider);

    provider.tick(event.endAt.plusSeconds(1));

    assertThat(event.status).isEqualTo(EventLifecycleStatus.POSTPONED);
  }

  @Test
  void suddenSuspensionChangesOneOpenMarket() {
    List<ProviderEvent.MarketStatusUpdated> updates = new ArrayList<>();
    var subscription =
        provider
            .streamEvents(event.summary.eventId())
            .ofType(ProviderEvent.MarketStatusUpdated.class)
            .subscribe(updates::add);

    new SuddenMarketSuspend().apply(event, NOW, new Random(0), provider);
    subscription.dispose();

    assertThat(event.markets.values())
        .extracting(market -> market.status)
        .containsExactly(MarketStatus.SUSPENDED);
    assertThat(updates)
        .singleElement()
        .satisfies(
            update -> {
              assertThat(update.previousStatus()).isEqualTo(MarketStatus.OPEN);
              assertThat(update.newStatus()).isEqualTo(MarketStatus.SUSPENDED);
              assertThat(update.reason()).isNotBlank();
            });
  }
}
