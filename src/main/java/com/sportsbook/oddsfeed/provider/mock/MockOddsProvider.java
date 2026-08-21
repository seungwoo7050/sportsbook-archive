package com.sportsbook.oddsfeed.provider.mock;

import com.sportsbook.oddsfeed.config.MockProperties;
import com.sportsbook.oddsfeed.provider.EventSummary;
import com.sportsbook.oddsfeed.provider.MatchOutcome;
import com.sportsbook.oddsfeed.provider.OddsProvider;
import com.sportsbook.oddsfeed.provider.ProviderEvent;
import com.sportsbook.oddsfeed.provider.Sport;
import com.sportsbook.protocol.domain.MarketType;
import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.protocol.event.EventLifecycleStatus;
import com.sportsbook.protocol.event.MarketStatus;
import com.sportsbook.protocol.event.MatchFinalStatus;
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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Component
@Profile("mock")
public class MockOddsProvider implements OddsProvider {

  static final int INITIAL_EVENT_COUNT = 3;
  static final Duration MATCH_DURATION = Duration.ofMinutes(90);
  static final Duration KICKOFF_SPACING = Duration.ofMinutes(1);
  private static final int REPLAY_HISTORY = 256;
  private static final double SECONDS_PER_MINUTE = 60.0;
  private static final long ODDS_STREAM_SALT = 0x9E3779B97F4A7C15L;
  private static final long RESULT_STREAM_SALT = 0xD1B54A32D192ED03L;
  private static final int EVENT_ID_ROTATION = 31;
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
  private final Map<EventId, Sinks.Many<ProviderEvent>> streams = new ConcurrentHashMap<>();
  private final Map<EventId, MatchOutcome> outcomes = new ConcurrentHashMap<>();
  private long runSeed;
  private Random structureRandom;
  private Random oddsRandom;

  public MockOddsProvider(MockProperties properties, Clock clock) {
    this.properties = properties;
    this.clock = clock;
  }

  @PostConstruct
  void seed() {
    runSeed = properties.randomSeed() == 0 ? new Random().nextLong() : properties.randomSeed();
    structureRandom = new Random(runSeed);
    oddsRandom = new Random(runSeed ^ ODDS_STREAM_SALT);
    Instant now = clock.instant();
    for (int index = 0; index < INITIAL_EVENT_COUNT; index++) {
      Instant kickoff = now.plus(toRealDuration(KICKOFF_SPACING.multipliedBy(index)));
      Instant end = kickoff.plus(toRealDuration(MATCH_DURATION));
      MockEvent event = buildEvent(kickoff, end, index);
      events.put(event.summary.eventId(), event);
      streams.put(event.summary.eventId(), Sinks.many().replay().limit(REPLAY_HISTORY));
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

  void emit(EventId eventId, ProviderEvent event) {
    Sinks.Many<ProviderEvent> sink = streams.get(eventId);
    if (sink == null) {
      return;
    }
    Sinks.EmitResult result = sink.tryEmitNext(event);
    if (result.isFailure()) {
      throw new IllegalStateException("Could not emit mock provider event: " + result);
    }
  }

  @Scheduled(fixedRateString = "${oddsfeed.mock.tick-interval-ms:500}")
  void scheduledTick() {
    tick(clock.instant());
  }

  void tick(Instant now) {
    for (MockEvent event : events.values()) {
      advance(event, now);
    }
  }

  private void advance(MockEvent event, Instant now) {
    if (event.status == EventLifecycleStatus.FINISHED
        || event.status == EventLifecycleStatus.CANCELLED
        || event.status == EventLifecycleStatus.POSTPONED) {
      return;
    }
    if (event.status == EventLifecycleStatus.SCHEDULED && !now.isBefore(event.kickoffAt)) {
      transitionTo(event, EventLifecycleStatus.IN_PLAY, now);
    }
    if (event.status == EventLifecycleStatus.IN_PLAY && !now.isBefore(event.endAt)) {
      outcomes.put(event.summary.eventId(), synthesizeOutcome(event));
      transitionTo(event, EventLifecycleStatus.FINISHED, now);
      return;
    }
    for (MockMarket market : event.markets.values()) {
      if (market.status != MarketStatus.OPEN) {
        continue;
      }
      for (MockSelection selection : market.selections.values()) {
        Odds previous = selection.currentOdds;
        Odds next = OddsSimulator.nextOdds(previous, selection.impliedProbability, oddsRandom);
        if (!previous.equals(next)) {
          selection.currentOdds = next;
          emit(
              event.summary.eventId(),
              new ProviderEvent.OddsUpdated(
                  event.summary.eventId(),
                  market.marketId,
                  selection.selectionId,
                  previous,
                  next,
                  now));
        }
      }
    }
  }

  Iterable<MockEvent> activeEvents() {
    return events.values();
  }

  void transitionTo(MockEvent event, EventLifecycleStatus next, Instant now) {
    event.status = next;
    event.summary =
        new EventSummary(
            event.summary.eventId(),
            event.summary.sport(),
            event.summary.competition(),
            event.summary.homeTeam(),
            event.summary.awayTeam(),
            event.summary.scheduledStartAt(),
            next);
    emit(
        event.summary.eventId(),
        new ProviderEvent.LifecycleUpdated(
            event.summary.eventId(), next, event.summary.scheduledStartAt(), now));
  }

  private MatchOutcome synthesizeOutcome(MockEvent event) {
    double roll = resultRandom(event.summary.eventId()).nextDouble();
    String score;
    String winningSelection;
    if (roll < properties.baseHomeWinProbability()) {
      score = "2-1";
      winningSelection = "HOME";
    } else if (roll < properties.baseHomeWinProbability() + properties.baseDrawProbability()) {
      score = "1-1";
      winningSelection = "DRAW";
    } else {
      score = "0-1";
      winningSelection = "AWAY";
    }
    return new MatchOutcome(
        event.summary.eventId(),
        score,
        MatchFinalStatus.COMPLETED,
        gradeSelections(event, winningSelection),
        event.endAt);
  }

  private Map<String, String> gradeSelections(MockEvent event, String winningSelection) {
    Map<String, String> detail = new LinkedHashMap<>();
    for (MockMarket market : event.markets.values()) {
      for (MockSelection selection : market.selections.values()) {
        SettlementResult result =
            selection.name.equals(winningSelection) ? SettlementResult.WON : SettlementResult.LOST;
        detail.put(selection.selectionId.value().toString(), result.name());
      }
    }
    return detail;
  }

  private Random resultRandom(EventId eventId) {
    UUID value = eventId.value();
    return new Random(
        runSeed
            ^ value.getMostSignificantBits()
            ^ Long.rotateLeft(value.getLeastSignificantBits(), EVENT_ID_ROTATION)
            ^ RESULT_STREAM_SALT);
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
    Sinks.Many<ProviderEvent> sink = streams.get(eventId);
    return sink == null ? Flux.empty() : sink.asFlux();
  }

  @Override
  public Optional<MatchOutcome> getMatchResult(EventId eventId) {
    return Optional.ofNullable(outcomes.get(eventId));
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
