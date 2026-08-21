package com.sportsbook.oddsfeed.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.event.EventLifecycle;
import com.sportsbook.protocol.event.EventLifecycleStatus;
import com.sportsbook.protocol.event.OddsChanged;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AvroSerializerTest {

  @Test
  void roundTripsOddsChangesWithDecimalText() {
    OddsChanged original =
        new OddsChanged(
            "00000000-0000-0000-0000-000000000001",
            "00000000-0000-0000-0000-000000000002",
            "00000000-0000-0000-0000-000000000003",
            "1.8500",
            "1.9000",
            Instant.parse("2026-05-28T10:00:00Z"));

    byte[] encoded = new AvroSerializer<OddsChanged>().serialize("odds.changed", original);
    OddsChanged decoded =
        new AvroDeserializer<OddsChanged>(OddsChanged.getClassSchema())
            .deserialize("odds.changed", encoded);

    assertThat(decoded.getEventId()).isEqualTo(original.getEventId());
    assertThat(decoded.getMarketId()).isEqualTo(original.getMarketId());
    assertThat(decoded.getSelectionId()).isEqualTo(original.getSelectionId());
    assertThat(decoded.getPreviousOdds()).isEqualTo(original.getPreviousOdds());
    assertThat(decoded.getNewOdds()).isEqualTo(original.getNewOdds());
    assertThat(decoded.getChangedAt()).isEqualTo(original.getChangedAt());
  }

  @Test
  void roundTripsLifecycleTimestampsAndEnums() {
    Instant kickoff = Instant.parse("2026-06-01T18:00:00Z");
    Instant occurredAt = Instant.parse("2026-05-28T10:00:00Z");
    EventLifecycle original =
        new EventLifecycle(
            "00000000-0000-0000-0000-000000000001",
            EventLifecycleStatus.SCHEDULED,
            occurredAt,
            kickoff);

    byte[] encoded = new AvroSerializer<EventLifecycle>().serialize("event.lifecycle", original);
    EventLifecycle decoded =
        new AvroDeserializer<EventLifecycle>(EventLifecycle.getClassSchema())
            .deserialize("event.lifecycle", encoded);

    assertThat(decoded.getStatus()).isEqualTo(EventLifecycleStatus.SCHEDULED);
    assertThat(decoded.getOccurredAt()).isEqualTo(occurredAt);
    assertThat(decoded.getScheduledStartAt()).isEqualTo(kickoff);
  }

  @Test
  void preservesKafkaNullValues() {
    assertThat(new AvroSerializer<OddsChanged>().serialize("odds.changed", null)).isNull();
    assertThat(
            new AvroDeserializer<OddsChanged>(OddsChanged.getClassSchema())
                .deserialize("odds.changed", null))
        .isNull();
  }
}
