package com.sportsbook.oddsfeed.provider.mock;

import com.sportsbook.oddsfeed.config.MockProperties;
import com.sportsbook.oddsfeed.provider.EventSummary;
import com.sportsbook.oddsfeed.provider.MatchOutcome;
import com.sportsbook.oddsfeed.provider.OddsProvider;
import com.sportsbook.oddsfeed.provider.ProviderEvent;
import com.sportsbook.oddsfeed.provider.Sport;
import com.sportsbook.protocol.domain.MarketType;
import com.sportsbook.protocol.event.EventLifecycleStatus;
import com.sportsbook.protocol.event.MarketStatus;
import com.sportsbook.protocol.value.EventId;
import com.sportsbook.protocol.value.MarketId;
import com.sportsbook.protocol.value.Odds;
import com.sportsbook.protocol.value.SelectionId;
import jakarta.annotation.PostConstruct;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
@Profile("mock")
public class MockOddsProvider implements OddsProvider {

  static final int INITIAL_EVENT_COUNT = 3;
  static final Duration MATCH_DURATION = Duration.ofMinutes(90);
  static final Duration KICKOFF_SPACING = Duration.ofMinutes(1);
  private static final double SECONDS_PER_MINUTE = 60.0;
  private static final String[] FOOTBALL_TEAMS = {
    "Manchester United",
    "Chelsea",
    "Liverpool",
    "Arsenal",
    "Tottenham",
    "Manchester City",
    "Newcastle",
    "Brighton"
  };

  private final MockProperties properties;
  private final Clock clock;
  private final Map<EventId, MockEvent> events = new ConcurrentHashMap<>();
  private long runSeed;
  private Random structureRandom;

  public MockOddsProvider(MockProperties properties, Clock clock) {
    this.properties = properties;
    this.clock = clock;
  }

  @PostConstruct
  void seed() {
    runSeed = properties.randomSeed() == 0 ? new Random().nextLong() : properties.randomSeed();
    structureRandom = new Random(runSeed);
    Instant now = clock.instant();
    for (int index = 0; index < INITIAL_EVENT_COUNT; index++) {
      Instant kickoff = now.plus(toRealDuration(KICKOFF_SPACING.multipliedBy(index)));
      Instant end = kickoff.plus(toRealDuration(MATCH_DURATION));
      MockEvent event = buildEvent(kickoff, end, index);
      events.put(event.summary.eventId(), event);
    }
  }

  private MockEvent buildEvent(Instant kickoff, Instant end, int index) {
    EventId eventId = new EventId(nextUuid());
    MarketId marketId = new MarketId(nextUuid());
    Map<SelectionId, MockSelection> selections = new LinkedHashMap<>();
    addSelection(selections, "HOME", properties.baseHomeWinProbability());
    addSelection(selections, "DRAW", properties.baseDrawProbability());
    addSelection(selections, "AWAY", properties.baseAwayWinProbability());

    MockMarket market = new MockMarket(marketId, MarketType.MATCH_RESULT_1X2, selections);
    int homeIndex = (index * 2) % FOOTBALL_TEAMS.length;
    EventSummary summary =
        new EventSummary(
            eventId,
            Sport.FOOTBALL,
            "Premier League",
            FOOTBALL_TEAMS[homeIndex],
            FOOTBALL_TEAMS[(homeIndex + 1) % FOOTBALL_TEAMS.length],
            kickoff,
            EventLifecycleStatus.SCHEDULED);

    MockEvent event = new MockEvent();
    event.summary = summary;
    event.markets.put(marketId, market);
    event.kickoffAt = kickoff;
    event.endAt = end;
    event.status = EventLifecycleStatus.SCHEDULED;
    return event;
  }

  private void addSelection(
      Map<SelectionId, MockSelection> selections, String name, double probability) {
    SelectionId selectionId = new SelectionId(nextUuid());
    selections.put(
        selectionId,
        new MockSelection(selectionId, name, probability, OddsSimulator.initialOdds(probability)));
  }

  private UUID nextUuid() {
    return new UUID(structureRandom.nextLong(), structureRandom.nextLong());
  }

  private Duration toRealDuration(Duration mockDuration) {
    double mockMinutes = mockDuration.toSeconds() / SECONDS_PER_MINUTE;
    long realSeconds = Math.max(1L, (long) (mockMinutes / properties.minutesPerSecond()));
    return Duration.ofSeconds(realSeconds);
  }

  MockProperties properties() {
    return properties;
  }

  Clock clock() {
    return clock;
  }

  @Override
  public List<EventSummary> listEvents(Sport sport) {
    List<EventSummary> result = new ArrayList<>();
    for (MockEvent event : events.values()) {
      if (event.summary.sport() == sport) {
        result.add(event.summary);
      }
    }
    return List.copyOf(result);
  }

  @Override
  public Flux<ProviderEvent> streamEvents(EventId eventId) {
    return Flux.empty();
  }

  @Override
  public Optional<MatchOutcome> getMatchResult(EventId eventId) {
    return Optional.empty();
  }

  static final class MockEvent {
    EventSummary summary;
    final Map<MarketId, MockMarket> markets = new ConcurrentHashMap<>();
    Instant kickoffAt;
    Instant endAt;
    EventLifecycleStatus status;
  }

  static final class MockMarket {
    final MarketId marketId;
    final MarketType type;
    MarketStatus status = MarketStatus.OPEN;
    final Map<SelectionId, MockSelection> selections;

    MockMarket(MarketId marketId, MarketType type, Map<SelectionId, MockSelection> selections) {
      this.marketId = marketId;
      this.type = type;
      this.selections = selections;
    }
  }

  static final class MockSelection {
    final SelectionId selectionId;
    final String name;
    final double impliedProbability;
    Odds currentOdds;

    MockSelection(SelectionId id, String name, double impliedProbability, Odds initial) {
      this.selectionId = id;
      this.name = name;
      this.impliedProbability = impliedProbability;
      this.currentOdds = initial;
    }
  }
}
