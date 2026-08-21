package com.sportsbook.oddsfeed.provider;

import com.sportsbook.protocol.value.EventId;
import java.util.List;
import java.util.Optional;
import reactor.core.publisher.Flux;

public interface OddsProvider {

  List<EventSummary> listEvents(Sport sport);

  Flux<ProviderEvent> streamEvents(EventId eventId);

  Optional<MatchOutcome> getMatchResult(EventId eventId);
}
