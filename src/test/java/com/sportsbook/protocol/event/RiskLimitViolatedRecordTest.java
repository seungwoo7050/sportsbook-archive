package com.sportsbook.protocol.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class RiskLimitViolatedRecordTest {

  @Test
  void riskLimitSignalRoundTrips() throws Exception {
    RiskLimitViolated expected =
        RiskLimitViolated.newBuilder()
            .setUserId("user-1")
            .setLimitType(RiskLimitType.STAKE_DAILY)
            .setCurrentValue(90_000)
            .setLimitValue(100_000)
            .setRequestedAmount(Money.newBuilder().setAmount(20_000).setCurrency("KRW").build())
            .setOccurredAt(Instant.parse("2026-08-21T00:00:00Z"))
            .build();
    AvroRecordTestSupport.assertFields(
        RiskLimitViolated.getClassSchema(),
        "userId",
        "limitType",
        "currentValue",
        "limitValue",
        "requestedAmount",
        "occurredAt");
    assertThat(AvroRecordTestSupport.roundTrip(expected, RiskLimitViolated.class))
        .isEqualTo(expected);
  }
}
