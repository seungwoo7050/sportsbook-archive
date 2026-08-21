package com.sportsbook.wallet.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OutboxLeaseTest {

  private static final UUID EVENT_ID = UUID.fromString("0198ca71-8000-7000-8000-000000000001");
  private static final Instant UNTIL = Instant.parse("2026-08-21T00:00:30Z");

  @Test
  void ownershipRequiresTheSameWorkerAndFencingVersion() {
    OutboxLease lease = new OutboxLease(EVENT_ID, "worker-a", 3, UNTIL);

    assertThat(lease.isOwnedBy("worker-a", 3)).isTrue();
    assertThat(lease.isOwnedBy("worker-b", 3)).isFalse();
    assertThat(lease.isOwnedBy("worker-a", 2)).isFalse();
  }

  @Test
  void rejectsAnUnfencedLease() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new OutboxLease(EVENT_ID, "worker-a", 0, UNTIL));
  }

  @Test
  void leasedMessagesProtectTheirPayload() {
    byte[] payload = {1, 2};
    LeasedOutboxMessage message =
        new LeasedOutboxMessage(
            new OutboxLease(EVENT_ID, "worker-a", 1, UNTIL),
            "wallet.debited.v1",
            "bet-1",
            "WalletDebited",
            payload,
            5L,
            true,
            1,
            Instant.parse("2026-08-21T00:00:00Z"));

    payload[0] = 9;
    byte[] exposed = message.payload();
    exposed[1] = 9;

    assertThat(message.payload()).containsExactly(1, 2);
    assertThat(message.streamSequence()).isEqualTo(5L);
    assertThat(message.leaseTakeover()).isTrue();
  }
}
