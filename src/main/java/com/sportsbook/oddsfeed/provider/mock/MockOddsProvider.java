package com.sportsbook.oddsfeed.provider.mock;

import com.sportsbook.oddsfeed.config.MockProperties;
import com.sportsbook.oddsfeed.provider.EventSummary;
import com.sportsbook.oddsfeed.provider.MatchOutcome;
import com.sportsbook.oddsfeed.provider.OddsProvider;
import com.sportsbook.oddsfeed.provider.ProviderEvent;
import com.sportsbook.oddsfeed.provider.Sport;
import com.sportsbook.protocol.value.EventId;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
@Profile("mock")
public class MockOddsProvider implements OddsProvider {

  private final MockProperties properties;
  private final Clock clock;

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
    return List.of();
  }

  @Override
  public Flux<ProviderEvent> streamEvents(EventId eventId) {
    return Flux.empty();
  }

  @Override
  public Optional<MatchOutcome> getMatchResult(EventId eventId) {
    return Optional.empty();
  }
}
