package com.sportsbook.wallet.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PendingOutboxMessageTest {

  @Test
  void copiesThePayloadAtConstruction() {
    byte[] payload = {1, 2, 3};

    PendingOutboxMessage message = message(payload);
    payload[0] = 9;

    assertThat(message.payload()).containsExactly(1, 2, 3);
  }

  @Test
  void neverExposesItsPayloadArray() {
    PendingOutboxMessage message = message(new byte[] {1, 2, 3});

    byte[] exposed = message.payload();
    exposed[1] = 9;

    assertThat(message.payload()).containsExactly(1, 2, 3);
  }

  private PendingOutboxMessage message(byte[] payload) {
    Instant now = Instant.parse("2026-08-21T00:00:00Z");
    return new PendingOutboxMessage(
        UUID.fromString("0198ca71-8000-7000-8000-000000000001"),
        "operation-1",
        "wallet.debited.v1",
        "bet-1",
        "WalletDebited",
        "debit:bet-1",
        payload,
        now);
  }
}
