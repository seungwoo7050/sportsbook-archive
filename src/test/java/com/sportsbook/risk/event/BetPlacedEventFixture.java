package com.sportsbook.risk.event;

import com.sportsbook.protocol.event.BetPlacedRequested;
import com.sportsbook.protocol.event.BetSlipTypeTag;
import com.sportsbook.protocol.event.Money;
import com.sportsbook.protocol.event.RequestedSelection;
import java.time.Instant;
import java.util.List;

final class BetPlacedEventFixture {
  static final String USER_ID = "10000000-0000-4000-8000-000000000001";
  static final String OTHER_USER_ID = "10000000-0000-4000-8000-000000000002";
  static final Instant REQUESTED_AT = Instant.parse("2026-08-21T06:00:00Z");
  static final Instant OBSERVED_AT = REQUESTED_AT.plusSeconds(1);

  private BetPlacedEventFixture() {}

  static byte[] payload() {
    RequestedSelection selection =
        new RequestedSelection(
            "30000000-0000-4000-8000-000000000001",
            "40000000-0000-4000-8000-000000000001",
            "50000000-0000-4000-8000-000000000001",
            "1.85");
    BetPlacedRequested event =
        new BetPlacedRequested(
            "20000000-0000-4000-8000-000000000001",
            USER_ID,
            BetSlipTypeTag.SINGLE,
            null,
            null,
            List.of(selection),
            new Money(10_000L, "KRW"),
            "accepted-bet-fixture",
            REQUESTED_AT);
    return AvroCodec.encode(event);
  }
}
