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
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
@Profile("mock")
public class MockOddsProvider implements OddsProvider {

  private final MockProperties properties;
  private final Clock clock;
  private final Map<EventId, MockEvent> events = new ConcurrentHashMap<>();

  public MockOddsProvider(MockProperties properties, Clock clock) {
    this.properties = properties;
    this.clock = clock;
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
