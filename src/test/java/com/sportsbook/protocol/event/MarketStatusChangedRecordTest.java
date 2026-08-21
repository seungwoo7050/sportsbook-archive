package com.sportsbook.protocol.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class MarketStatusChangedRecordTest {

  @Test
  void marketStatusChangeRoundTrips() throws Exception {
    MarketStatusChanged expected =
        MarketStatusChanged.newBuilder()
            .setEventId("event-1")
            .setMarketId("market-1")
            .setPreviousStatus(MarketStatus.OPEN)
            .setNewStatus(MarketStatus.SUSPENDED)
            .setReason("goal scored")
            .setOccurredAt(Instant.parse("2026-08-21T00:00:00Z"))
            .build();
    AvroRecordTestSupport.assertFields(
        MarketStatusChanged.getClassSchema(),
        "eventId",
        "marketId",
        "previousStatus",
        "newStatus",
        "reason",
        "occurredAt");
    assertThat(AvroRecordTestSupport.roundTrip(expected, MarketStatusChanged.class))
        .isEqualTo(expected);
  }
}
