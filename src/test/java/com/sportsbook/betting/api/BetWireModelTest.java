package com.sportsbook.betting.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.betting.domain.Bet;
import com.sportsbook.betting.domain.BetDraft;
import com.sportsbook.betting.domain.BetLeg;
import com.sportsbook.protocol.domain.BetSlipType;
import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.Odds;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BetWireModelTest {

  @Test
  void neverAcceptsActorIdentityFromThePlacementBody() {
    assertThat(
            Arrays.stream(PlaceBetRequest.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName))
        .doesNotContain("userId");
  }

  @Test
  void rendersSystemShapeAndOriginalUnitStake() {
    BetSlipType type = new BetSlipType.System(2, 3);
    Bet bet =
        Bet.pending(
            new BetDraft(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "B-2026-08-22-00000000",
                type,
                Money.krw(1_000),
                Money.krw(10_000),
                IdempotencyKey.of("request-1"),
                "a".repeat(64),
                Instant.EPOCH),
            List.of(leg(), leg(), leg()));
    bet.recordRiskReservation(Instant.EPOCH.plusSeconds(60), "b".repeat(64), true, Instant.EPOCH);
    bet.confirmWallet(UUID.randomUUID(), Instant.EPOCH);
    bet.commitRisk(Instant.EPOCH);
    bet.accept(Instant.EPOCH);

    BetResponse response = BetResponse.from(bet);

    assertThat(response.slipType()).isEqualTo(new BetResponse.SlipTypeView("SYSTEM", 2, 3));
    assertThat(response.stake()).isEqualTo(Money.krw(1_000));
    assertThat(response.selections()).hasSize(3);
  }

  private static BetLeg leg() {
    return BetLeg.create(
        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Odds.ofDecimal("2"));
  }
}
