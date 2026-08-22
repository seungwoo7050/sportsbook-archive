package com.sportsbook.betting.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.betting.domain.Bet;
import com.sportsbook.betting.domain.BetDraft;
import com.sportsbook.betting.domain.BetLeg;
import com.sportsbook.protocol.domain.BetSlipType;
import com.sportsbook.protocol.event.BetPlacedRequested;
import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.Odds;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class BetEventFactoryTest {

  @Test
  void publishesSystemUnitStakeRatherThanExposure() {
    BetDraft draft =
        new BetDraft(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "B-2026-08-22-00000000",
            new BetSlipType.System(2, 3),
            Money.krw(100),
            Money.krw(2_600),
            IdempotencyKey.of("request-1"),
            "a".repeat(64),
            Instant.EPOCH);
    Bet bet = Bet.pending(draft, List.of(leg(), leg(), leg()));

    OutboxEvent event = new BetEventFactory().placedRequested(bet, Instant.EPOCH);
    BetPlacedRequested payload =
        AvroSerializer.deserialize(event.payload(), BetPlacedRequested.class);

    assertThat(payload.getStake().getAmount()).isEqualTo(100);
    assertThat(payload.getSelections()).hasSize(3);
    assertThat(event.partitionKey()).isEqualTo(bet.userId().toString());
  }

  @ParameterizedTest
  @MethodSource("nonSystemTypes")
  void omitsBothSystemFieldsForNonSystemSlips(BetSlipType type) {
    List<BetLeg> legs = type instanceof BetSlipType.Single ? List.of(leg()) : List.of(leg(), leg());
    Bet bet =
        Bet.pending(
            new BetDraft(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "B-2026-08-22-00000001",
                type,
                Money.krw(100),
                Money.krw(200),
                IdempotencyKey.of("request-2"),
                "b".repeat(64),
                Instant.EPOCH),
            legs);

    BetPlacedRequested payload =
        AvroSerializer.deserialize(
            new BetEventFactory().placedRequested(bet, Instant.EPOCH).payload(),
            BetPlacedRequested.class);

    assertThat(payload.getSystemMinWins()).isNull();
    assertThat(payload.getSystemTotalSelections()).isNull();
  }

  static List<BetSlipType> nonSystemTypes() {
    return List.of(new BetSlipType.Single(), new BetSlipType.Multiple());
  }

  private BetLeg leg() {
    return BetLeg.create(
        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Odds.ofDecimal("2.0"));
  }
}
