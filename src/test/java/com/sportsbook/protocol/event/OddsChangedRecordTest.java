package com.sportsbook.protocol.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class OddsChangedRecordTest {

  @Test
  void oddsChangeRoundTrips() throws Exception {
    OddsChanged expected =
        OddsChanged.newBuilder()
            .setEventId("event-1")
            .setMarketId("market-1")
            .setSelectionId("selection-1")
            .setPreviousOdds("1.8500")
            .setNewOdds("1.9100")
            .setChangedAt(Instant.parse("2026-08-21T00:00:00Z"))
            .build();
    AvroRecordTestSupport.assertFields(
        OddsChanged.getClassSchema(),
        "eventId",
        "marketId",
        "selectionId",
        "previousOdds",
        "newOdds",
        "changedAt");
    assertThat(AvroRecordTestSupport.roundTrip(expected, OddsChanged.class)).isEqualTo(expected);
  }
}
