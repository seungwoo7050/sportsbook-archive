package com.sportsbook.protocol.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BetSettledRecordTest {

  @Test
  void settledOutcomeRoundTrips() throws Exception {
    BetSettled expected =
        BetSettled.newBuilder()
            .setBetId("bet-1")
            .setUserId("user-1")
            .setEventId("event-1")
            .setResult(SettlementResultAvro.WON)
            .setStake(Money.newBuilder().setAmount(10_000).setCurrency("KRW").build())
            .setPayout(Money.newBuilder().setAmount(18_500).setCurrency("KRW").build())
            .setSettledAt(Instant.parse("2026-08-21T00:00:00Z"))
            .setResultDetail(Map.of("selection-1", "WON"))
            .build();
    AvroRecordTestSupport.assertFields(
        BetSettled.getClassSchema(),
        "betId",
        "userId",
        "eventId",
        "result",
        "stake",
        "payout",
        "settledAt",
        "resultDetail");
    assertThat(AvroRecordTestSupport.roundTrip(expected, BetSettled.class)).isEqualTo(expected);
  }
}
