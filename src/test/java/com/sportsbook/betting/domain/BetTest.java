package com.sportsbook.betting.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.domain.BetSlipType;
import com.sportsbook.protocol.domain.BetStatus;
import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.Money;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BetTest {

  static final Instant NOW = Instant.parse("2026-08-22T00:00:00Z");
  static final String FINGERPRINT = "a".repeat(64);

  @Test
  void beginsPendingWithOwnedIdentity() {
    UUID betId = UUID.randomUUID();
    Bet bet = Bet.from(draft(betId, new BetSlipType.Single()));

    assertThat(bet.betId()).isEqualTo(betId);
    assertThat(bet.status()).isEqualTo(BetStatus.PENDING);
    assertThat(bet.idempotencyKey()).isEqualTo("request-1");
    assertThat(bet.createdAt()).isEqualTo(NOW);
  }

  @Test
  void retainsSystemShapeAndWagerValues() {
    Bet bet = Bet.from(draft(UUID.randomUUID(), new BetSlipType.System(2, 3)));

    assertThat(bet.slipType()).isEqualTo(new BetSlipType.System(2, 3));
    assertThat(bet.stake()).isEqualTo(Money.krw(1_000));
    assertThat(bet.maxPayout()).isEqualTo(Money.krw(2_000));
    assertThat(bet.requestFingerprint()).isEqualTo(FINGERPRINT);
  }

  @Test
  void assignsLegOrderWhenCreatingAggregate() {
    Bet bet =
        Bet.pending(
            draft(UUID.randomUUID(), new BetSlipType.Multiple()),
            List.of(leg("2.00"), leg("3.00")));

    assertThat(bet.legs()).extracting(BetLeg::legIndex).containsExactly(0, 1);
  }

  @Test
  void storesOpaqueRiskProofBeforeWalletWork() {
    Bet bet = Bet.pending(draft(UUID.randomUUID(), new BetSlipType.Single()), List.of(leg("2.0")));
    Instant expires = NOW.plusSeconds(120);

    bet.recordRiskReservation(expires, "b".repeat(64), false, NOW.plusSeconds(1));

    assertThat(bet.placementPhase()).isEqualTo(PlacementPhase.RISK_RESERVED);
    assertThat(bet.riskReservationExpiresAt()).isEqualTo(expires);
    assertThat(bet.riskReservationToken()).isEqualTo("b".repeat(64));
    assertThat(bet.riskCommitObserved()).isFalse();
  }

  @Test
  void rejectsSlipShapeMismatch() {
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                Bet.pending(
                    draft(UUID.randomUUID(), new BetSlipType.Single()),
                    List.of(leg("2.00"), leg("3.00"))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("exactly one");
  }

  static BetLeg leg(String odds) {
    return BetLeg.create(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        com.sportsbook.protocol.value.Odds.ofDecimal(odds));
  }

  static BetDraft draft(UUID betId, BetSlipType type) {
    return new BetDraft(
        betId,
        UUID.randomUUID(),
        "B-2026-08-22-00000000",
        type,
        Money.krw(1_000),
        Money.krw(2_000),
        IdempotencyKey.of("request-1"),
        FINGERPRINT,
        NOW);
  }
}
