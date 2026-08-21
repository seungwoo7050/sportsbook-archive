package com.sportsbook.protocol.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RiskPatternSuspectedRecordTest {

  @Test
  void riskPatternSignalRoundTrips() throws Exception {
    RiskPatternSuspected expected =
        RiskPatternSuspected.newBuilder()
            .setUserId("user-1")
            .setPatternType(RiskPatternType.RAPID_BETTING)
            .setEvidence(Map.of("windowSeconds", "60", "attempts", "12"))
            .setOccurredAt(Instant.parse("2026-08-21T00:00:00Z"))
            .build();
    AvroRecordTestSupport.assertFields(
        RiskPatternSuspected.getClassSchema(), "userId", "patternType", "evidence", "occurredAt");
    assertThat(AvroRecordTestSupport.roundTrip(expected, RiskPatternSuspected.class))
        .isEqualTo(expected);
  }
}
