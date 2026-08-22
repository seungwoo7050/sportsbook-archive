package com.sportsbook.betting.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OutboxEventTest {

  @Test
  void ownsPayloadAndKeepsFirstPublicationProof() {
    byte[] source = {1, 2};
    OutboxEvent event =
        OutboxEvent.pending(UUID.randomUUID(), "topic", "key", "Schema", source, Instant.EPOCH);
    source[0] = 9;
    event.payload()[1] = 9;

    Instant first = Instant.parse("2026-08-22T00:00:00Z");
    event.markPublished(first);
    event.markPublished(first.plusSeconds(1));

    assertThat(event.payload()).containsExactly(1, 2);
    assertThat(event.publishedAt()).isEqualTo(first);
  }
}
