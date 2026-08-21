package com.sportsbook.protocol.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class EventLifecycleRecordTest {

  @Test
  void eventLifecycleChangeRoundTrips() throws Exception {
    EventLifecycle expected =
        EventLifecycle.newBuilder()
            .setEventId("event-1")
            .setStatus(EventLifecycleStatus.IN_PLAY)
            .setOccurredAt(Instant.parse("2026-08-21T00:00:00Z"))
            .setScheduledStartAt(Instant.parse("2026-08-21T00:00:00Z"))
            .build();
    AvroRecordTestSupport.assertFields(
        EventLifecycle.getClassSchema(), "eventId", "status", "occurredAt", "scheduledStartAt");
    assertThat(AvroRecordTestSupport.roundTrip(expected, EventLifecycle.class)).isEqualTo(expected);
  }
}
