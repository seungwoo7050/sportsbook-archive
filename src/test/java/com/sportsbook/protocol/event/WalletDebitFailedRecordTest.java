package com.sportsbook.protocol.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class WalletDebitFailedRecordTest {

  @Test
  void debitFailureRoundTrips() throws Exception {
    WalletDebitFailed expected =
        WalletDebitFailed.newBuilder()
            .setUserId("user-1")
            .setRequestedAmount(Money.newBuilder().setAmount(10_000).setCurrency("KRW").build())
            .setIdempotencyKey("bet-1:debit")
            .setReason(WalletDebitFailureReason.INSUFFICIENT_BALANCE)
            .setOccurredAt(Instant.parse("2026-08-21T00:00:00Z"))
            .build();
    AvroRecordTestSupport.assertFields(
        WalletDebitFailed.getClassSchema(),
        "userId",
        "requestedAmount",
        "idempotencyKey",
        "reason",
        "occurredAt");
    assertThat(AvroRecordTestSupport.roundTrip(expected, WalletDebitFailed.class))
        .isEqualTo(expected);
  }
}
