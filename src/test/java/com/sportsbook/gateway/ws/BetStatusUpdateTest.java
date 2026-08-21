package com.sportsbook.gateway.ws;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.event.BetResolutionRevised;
import com.sportsbook.protocol.event.Money;
import com.sportsbook.protocol.event.SettlementResultAvro;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BetStatusUpdateTest {

  @ParameterizedTest
  @CsvSource({"LOST,WON,0,18500", "WON,LOST,18500,0"})
  void projectsTheLatestRevisionSnapshot(
      SettlementResultAvro previousResult,
      SettlementResultAvro newResult,
      long previousPayout,
      long newPayout) {
    BetResolutionRevised event =
        BetResolutionRevised.newBuilder()
            .setRevisionId(UUID.randomUUID().toString())
            .setRevisionNumber(3)
            .setBetId(UUID.randomUUID().toString())
            .setUserId(UUID.randomUUID().toString())
            .setEventId(UUID.randomUUID().toString())
            .setPreviousResult(previousResult)
            .setNewResult(newResult)
            .setPreviousPayout(money(previousPayout))
            .setNewPayout(money(newPayout))
            .setSourceResultSettledAt(Instant.parse("2026-08-21T00:00:00Z"))
            .setRevisedAt(Instant.parse("2026-08-21T00:00:03Z"))
            .build();

    assertThat(BetStatusUpdate.revised(event))
        .isEqualTo(
            new BetStatusUpdate(
                event.getBetId(),
                event.getUserId(),
                event.getEventId(),
                "SETTLED",
                newResult.name(),
                new BetStatusUpdate.MoneyView(newPayout, "KRW"),
                null,
                event.getRevisionId(),
                3L,
                event.getRevisedAt()));
  }

  private static Money money(long amount) {
    return Money.newBuilder().setAmount(amount).setCurrency("KRW").build();
  }
}
