package com.sportsbook.protocol.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class BetVoidedRecordTest {

  @Test
  void voidedOutcomeRoundTrips() throws Exception {
    BetVoided expected =
        BetVoided.newBuilder()
            .setBetId("bet-1")
            .setUserId("user-1")
            .setEventId("event-1")
            .setReason(VoidReason.EVENT_CANCELLED)
            .setRefund(Money.newBuilder().setAmount(10_000).setCurrency("KRW").build())
            .setVoidedAt(Instant.parse("2026-08-21T00:00:00Z"))
            .build();
    AvroRecordTestSupport.assertFields(
        BetVoided.getClassSchema(), "betId", "userId", "eventId", "reason", "refund", "voidedAt");
    assertThat(AvroRecordTestSupport.roundTrip(expected, BetVoided.class)).isEqualTo(expected);
  }
}
