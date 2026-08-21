package com.sportsbook.protocol.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MatchResultRecordTest {

  @Test
  void matchResultRoundTrips() throws Exception {
    MatchResult expected =
        MatchResult.newBuilder()
            .setEventId("event-1")
            .setScore("2-1")
            .setFinalStatus(MatchFinalStatus.COMPLETED)
            .setResultDetail(Map.of("home", "2", "away", "1"))
            .setSettledAt(Instant.parse("2026-08-21T00:00:00Z"))
            .build();
    AvroRecordTestSupport.assertFields(
        MatchResult.getClassSchema(),
        "eventId",
        "score",
        "finalStatus",
        "resultDetail",
        "settledAt");
    assertThat(AvroRecordTestSupport.roundTrip(expected, MatchResult.class)).isEqualTo(expected);
  }
}
