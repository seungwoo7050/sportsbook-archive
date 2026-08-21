package com.sportsbook.protocol.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class BetResolutionPayoutIncreaseTest {

  @Test
  void lostBetCanBeRevisedToWonSnapshot() throws Exception {
    BetResolutionRevised expected =
        revision(SettlementResultAvro.LOST, 0, SettlementResultAvro.WON, 18_500);
    assertThat(AvroRecordTestSupport.roundTrip(expected, BetResolutionRevised.class))
        .isEqualTo(expected);
  }

  private BetResolutionRevised revision(
      SettlementResultAvro previousResult,
      long previousPayout,
      SettlementResultAvro newResult,
      long newPayout) {
    return BetResolutionRevised.newBuilder()
        .setRevisionId("0198f77e-dc00-7000-8000-000000000001")
        .setRevisionNumber(1)
        .setBetId("0198f77e-dc00-7000-8000-000000000002")
        .setUserId("0198f77e-dc00-7000-8000-000000000003")
        .setEventId("0198f77e-dc00-7000-8000-000000000004")
        .setPreviousResult(previousResult)
        .setNewResult(newResult)
        .setPreviousPayout(Money.newBuilder().setAmount(previousPayout).setCurrency("KRW").build())
        .setNewPayout(Money.newBuilder().setAmount(newPayout).setCurrency("KRW").build())
        .setSourceResultSettledAt(Instant.parse("2026-08-21T00:00:00Z"))
        .setRevisedAt(Instant.parse("2026-08-21T00:00:01Z"))
        .build();
  }
}
