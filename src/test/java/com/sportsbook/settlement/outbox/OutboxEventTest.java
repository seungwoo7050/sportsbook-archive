package com.sportsbook.settlement.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class OutboxEventTest {

  @Test
  void ownsRawPayloadAndStableRoutingIdentity() {
    byte[] source = {1, 2, 3};
    OutboxEvent event =
        OutboxEvent.pending("bet.settled.v1", "event-id", "BetSettled", source, Instant.EPOCH);
    source[0] = 9;
    byte[] returned = event.payload();
    returned[1] = 9;

    assertThat(event.eventId().version()).isEqualTo(7);
    assertThat(event.topic()).isEqualTo("bet.settled.v1");
    assertThat(event.partitionKey()).isEqualTo("event-id");
    assertThat(event.payload()).containsExactly(1, 2, 3);
  }

  @Test
  void marksPublicationOnce() {
    OutboxEvent event =
        OutboxEvent.pending("bet.voided.v1", "event-id", "BetVoided", new byte[0], Instant.EPOCH);
    Instant first = Instant.parse("2026-01-01T00:00:00Z");

    event.markPublished(first);
    event.markPublished(first.plusSeconds(1));

    assertThat(event.publishedAt()).isEqualTo(first);
  }

  @Test
  void rejectsBlankRoutingFields() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> OutboxEvent.pending(" ", "event-id", "BetSettled", new byte[0], Instant.EPOCH));
  }
}
