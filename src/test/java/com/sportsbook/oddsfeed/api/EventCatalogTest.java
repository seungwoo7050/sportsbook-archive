package com.sportsbook.oddsfeed.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.oddsfeed.provider.EventSummary;
import com.sportsbook.oddsfeed.provider.Sport;
import com.sportsbook.protocol.event.EventLifecycleStatus;
import com.sportsbook.protocol.value.EventId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventCatalogTest {

  @Test
  void ordersSnapshotsByKickoffAndIdentity() {
    EventCatalog catalog = new EventCatalog();
    Instant kickoff = Instant.parse("2026-06-01T18:00:00Z");
    EventSummary second = event("00000000-0000-0000-0000-000000000002", kickoff);
    EventSummary first = event("00000000-0000-0000-0000-000000000001", kickoff);
    EventSummary earlier = event("00000000-0000-0000-0000-000000000003", kickoff.minusSeconds(60));

    assertThat(catalog.putIfAbsent(second)).isTrue();
    assertThat(catalog.putIfAbsent(second)).isFalse();
    catalog.put(first);
    catalog.put(earlier);

    assertThat(catalog.orderedByKickoff()).containsExactly(earlier, first, second);
    assertThat(catalog.get(first.eventId())).contains(first);
    assertThat(catalog.size()).isEqualTo(3);
  }

  private static EventSummary event(String id, Instant kickoff) {
    return new EventSummary(
        new EventId(UUID.fromString(id)),
        Sport.FOOTBALL,
        "Premier League",
        "Home",
        "Away",
        kickoff,
        EventLifecycleStatus.SCHEDULED);
  }
}
