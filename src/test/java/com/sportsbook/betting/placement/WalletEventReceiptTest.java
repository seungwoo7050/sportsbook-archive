package com.sportsbook.betting.placement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WalletEventReceiptTest {

  @Test
  void ownsOneValidWalletWakeEventIdentity() {
    UUID eventId = UUID.randomUUID();
    UUID betId = UUID.randomUUID();
    WalletEventReceipt receipt =
        WalletEventReceipt.pending(
            eventId, "wallet.debited.v1", betId, UUID.randomUUID(), "a".repeat(64), Instant.EPOCH);

    receipt.markProcessed(Instant.EPOCH.plusSeconds(1));

    assertThat(receipt.eventId()).isEqualTo(eventId);
    assertThat(receipt.betId()).isEqualTo(betId);
    assertThat(receipt.processedAt()).isEqualTo(Instant.EPOCH.plusSeconds(1));
  }

  @Test
  void rejectsUnknownTopicsAndUnverifiablePayloads() {
    assertThatThrownBy(
            () ->
                WalletEventReceipt.pending(
                    UUID.randomUUID(),
                    "wallet.unknown.v1",
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "not-a-hash",
                    Instant.EPOCH))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
