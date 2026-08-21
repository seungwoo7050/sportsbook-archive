package com.sportsbook.oddsfeed.provider.mock;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.oddsfeed.config.MockProperties;
import com.sportsbook.oddsfeed.provider.EventSummary;
import com.sportsbook.oddsfeed.provider.ProviderEvent;
import com.sportsbook.protocol.event.EventLifecycleStatus;
import com.sportsbook.protocol.event.MarketStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
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

  @Test
  void oddsCrashPublishesOneSharpPriceChange() {
    List<ProviderEvent.OddsUpdated> updates = new ArrayList<>();
    var subscription =
        provider
            .streamEvents(event.summary.eventId())
            .ofType(ProviderEvent.OddsUpdated.class)
            .subscribe(updates::add);

    new OddsCrash().apply(event, NOW, new Random(0), provider);
    subscription.dispose();

    assertThat(updates)
        .singleElement()
        .satisfies(
            update -> {
              var ratio =
                  update
                      .newOdds()
                      .decimal()
                      .divide(update.previousOdds().decimal(), 4, RoundingMode.HALF_EVEN);
              assertThat(ratio).isLessThan(new BigDecimal("0.5"));
              assertThat(update.occurredAt()).isEqualTo(NOW);
            });
  }

  @Test
  void lateGoalOnlyAppliesNearTheEndOfPlay() {
    LateGoal scenario = new LateGoal();
    assertThat(scenario.canApply(event, NOW)).isFalse();

    provider.tick(event.kickoffAt);
    Instant nearEnd = event.endAt.minusSeconds(1);
    List<ProviderEvent.OddsUpdated> updates = new ArrayList<>();
    var subscription =
        provider
            .streamEvents(event.summary.eventId())
            .ofType(ProviderEvent.OddsUpdated.class)
            .subscribe(updates::add);
    updates.clear();

    assertThat(scenario.canApply(event, nearEnd)).isTrue();
    scenario.apply(event, nearEnd, new Random(1), provider);
    subscription.dispose();

    assertThat(updates)
        .singleElement()
        .satisfies(update -> assertThat(update.newOdds()).isNotEqualTo(update.previousOdds()));
  }

  @Test
  void scenarioSetIsClosedToFourDisruptions() {
    assertThat(MockScenario.class.getPermittedSubclasses())
        .extracting(Class::getSimpleName)
        .containsExactlyInAnyOrder(
            "LateGoal", "MatchPostponed", "OddsCrash", "SuddenMarketSuspend");
  }
}
