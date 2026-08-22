package com.sportsbook.settlement.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.event.EventLifecycleStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LifecycleOrderTest {

  @Test
  void ordersByOccurrenceRatherThanDeliveryTime() {
    UUID eventId = UUID.randomUUID();
    LifecycleObservation earlier = observation(eventId, EventLifecycleStatus.IN_PLAY, 1, 100);
    LifecycleObservation later = observation(eventId, EventLifecycleStatus.FINISHED, 2, 3);

    assertThat(new LifecycleOrder().latest(List.of(later, earlier))).contains(later);
  }

  @Test
  void terminalStatusWinsAnEqualOccurrenceBoundary() {
    UUID eventId = UUID.randomUUID();
    LifecycleObservation scheduled = observation(eventId, EventLifecycleStatus.SCHEDULED, 1, 2);
    LifecycleObservation cancelled = observation(eventId, EventLifecycleStatus.CANCELLED, 1, 3);

    assertThat(new LifecycleOrder().latest(List.of(cancelled, scheduled))).contains(cancelled);
  }

  private static LifecycleObservation observation(
      UUID eventId, EventLifecycleStatus status, long occurredSecond, long receivedSecond) {
    return LifecycleObservation.observe(
        eventId,
        status,
        Instant.EPOCH.plusSeconds(occurredSecond),
        null,
        Instant.EPOCH.plusSeconds(receivedSecond));
  }
}
