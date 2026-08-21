package com.sportsbook.oddsfeed.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.value.EventId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class OddsProviderTest {

  @Test
  void supportsSnapshotStreamAndResultLookups() {
    OddsProvider provider =
        new OddsProvider() {
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
        };

    EventId eventId = new EventId(UUID.randomUUID());
    assertThat(provider.listEvents(Sport.FOOTBALL)).isEmpty();
    assertThat(provider.getMatchResult(eventId)).isEmpty();
    StepVerifier.create(provider.streamEvents(eventId)).verifyComplete();
  }
}
