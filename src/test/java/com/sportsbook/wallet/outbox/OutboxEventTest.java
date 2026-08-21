package com.sportsbook.wallet.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.Column;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class OutboxEventTest {
  private static final Instant NOW = Instant.parse("2026-01-05T00:00:00Z");

  @Test
  void mapsAnImmutableMessageAtItsCommittedStreamPosition() throws NoSuchFieldException {
    byte[] payload = {1, 2, 3};
    PendingOutboxMessage message =
        PendingOutboxMessage.create(
            "operation-1", "wallet.debited.v1", "user-1", "WalletDebited", "bet-1", payload, NOW);

    OutboxEvent event = OutboxEvent.pending(message, 7L);
    payload[0] = 9;
    byte[] exposed = event.payload();
    exposed[1] = 9;

    assertThat(event.operationKey()).isEqualTo("operation-1");
    assertThat(event.topic()).isEqualTo("wallet.debited.v1");
    assertThat(event.partitionKey()).isEqualTo("user-1");
    assertThat(event.schemaName()).isEqualTo("WalletDebited");
    assertThat(event.deduplicationKey()).isEqualTo("bet-1");
    assertThat(event.streamSequence()).isEqualTo(7L);
    assertThat(event.payload()).containsExactly(1, 2, 3);
    Column deadline = OutboxEvent.class.getDeclaredField("availableAt").getAnnotation(Column.class);
    assertThat(deadline.insertable()).isFalse();
    assertThat(deadline.updatable()).isFalse();
    assertThat(ReflectionTestUtils.getField(event, "availableAt")).isNull();
    assertThatThrownBy(() -> OutboxEvent.pending(message, 0L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("positive");
  }
}
