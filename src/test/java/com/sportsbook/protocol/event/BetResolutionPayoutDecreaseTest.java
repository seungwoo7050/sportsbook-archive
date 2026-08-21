package com.sportsbook.protocol.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class BetResolutionPayoutDecreaseTest {

  @Test
  void wonBetCanBeRevisedToLostSnapshot() throws Exception {
    BetResolutionRevised expected =
        BetResolutionRevised.newBuilder()
            .setRevisionId("0198f77e-dc00-7000-8000-000000000001")
            .setRevisionNumber(1)
            .setBetId("0198f77e-dc00-7000-8000-000000000002")
            .setUserId("0198f77e-dc00-7000-8000-000000000003")
            .setEventId("0198f77e-dc00-7000-8000-000000000004")
            .setPreviousResult(SettlementResultAvro.WON)
            .setNewResult(SettlementResultAvro.LOST)
            .setPreviousPayout(Money.newBuilder().setAmount(18_500).setCurrency("KRW").build())
            .setNewPayout(Money.newBuilder().setAmount(0).setCurrency("KRW").build())
            .setSourceResultSettledAt(Instant.parse("2026-08-21T00:00:00Z"))
            .setRevisedAt(Instant.parse("2026-08-21T00:00:01Z"))
            .build();
    assertThat(AvroRecordTestSupport.roundTrip(expected, BetResolutionRevised.class))
        .isEqualTo(expected);
  }
}
