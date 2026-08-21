package com.sportsbook.protocol.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class BetPlacedRequestedRecordTest {

  @Test
  void acceptedPlacementRoundTrips() throws Exception {
    RequestedSelection selection =
        RequestedSelection.newBuilder()
            .setEventId("event-1")
            .setMarketId("market-1")
            .setSelectionId("selection-1")
            .setOddsAtSubmission("1.8500")
            .build();
    BetPlacedRequested expected =
        BetPlacedRequested.newBuilder()
            .setBetId("bet-1")
            .setUserId("user-1")
            .setSlipType(BetSlipTypeTag.SINGLE)
            .setSystemMinWins(null)
            .setSystemTotalSelections(null)
            .setSelections(List.of(selection))
            .setStake(Money.newBuilder().setAmount(10_000).setCurrency("KRW").build())
            .setIdempotencyKey("placement-1")
            .setRequestedAt(Instant.parse("2026-08-21T00:00:00Z"))
            .build();
    AvroRecordTestSupport.assertFields(
        BetPlacedRequested.getClassSchema(),
        "betId",
        "userId",
        "slipType",
        "systemMinWins",
        "systemTotalSelections",
        "selections",
        "stake",
        "idempotencyKey",
        "requestedAt");
    assertThat(AvroRecordTestSupport.roundTrip(expected, BetPlacedRequested.class))
        .isEqualTo(expected);
  }
}
