package com.sportsbook.betting.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.protocol.domain.BetSlipType;
import com.sportsbook.protocol.domain.BetStatus;
import com.sportsbook.protocol.domain.SettlementResult;
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
  void advancesOnlyFromPersistedReservationToWalletProof() {
    Bet bet = Bet.pending(draft(UUID.randomUUID(), new BetSlipType.Single()), List.of(leg("2.0")));
    UUID operationId = UUID.randomUUID();
    bet.recordRiskReservation(NOW.plusSeconds(120), "c".repeat(64), false, NOW);

    bet.confirmWallet(operationId, NOW.plusSeconds(1));

    assertThat(bet.placementPhase()).isEqualTo(PlacementPhase.WALLET_CONFIRMED);
    assertThat(bet.walletOperationId()).isEqualTo(operationId);
  }

  @Test
  void acceptsOnlyAfterRiskCommitProof() {
    Bet bet = Bet.pending(draft(UUID.randomUUID(), new BetSlipType.Single()), List.of(leg("2.0")));
    bet.recordRiskReservation(NOW.plusSeconds(120), "d".repeat(64), false, NOW);
    bet.confirmWallet(UUID.randomUUID(), NOW);

    bet.commitRisk(NOW.plusSeconds(1));
    bet.accept(NOW.plusSeconds(2));

    assertThat(bet.placementPhase()).isEqualTo(PlacementPhase.RISK_COMMITTED);
    assertThat(bet.riskCommitObserved()).isTrue();
    assertThat(bet.status()).isEqualTo(BetStatus.ACCEPTED);
  }

  @Test
  void recordsRefundIntentBeforeCompensation() {
    Bet bet = Bet.pending(draft(UUID.randomUUID(), new BetSlipType.Single()), List.of(leg("2.0")));
    bet.recordRiskReservation(NOW.plusSeconds(120), "e".repeat(64), false, NOW);
    bet.confirmWallet(UUID.randomUUID(), NOW);

    bet.requireWalletRefund("DUPLICATE_BET", "risk commit conflict", NOW.plusSeconds(1));

    assertThat(bet.compensationAction()).isEqualTo(CompensationAction.WALLET_REFUND);
    assertThat(bet.compensationState()).isEqualTo(CompensationState.REQUIRED);
    assertThat(bet.rejectionReason()).isEqualTo("DUPLICATE_BET");
  }

  @Test
  void terminalizesCommittedReleaseConflictWithoutRetryLoop() {
    Bet bet = Bet.pending(draft(UUID.randomUUID(), new BetSlipType.Single()), List.of(leg("2.0")));
    bet.recordRiskReservation(NOW.plusSeconds(120), "f".repeat(64), false, NOW);
    bet.requireRiskRelease("INSUFFICIENT_BALANCE", "wallet declined", NOW);
    bet.beginCompensation(NOW.plusSeconds(1));

    bet.completeRiskRelease(true, NOW.plusSeconds(2));
    bet.rejectAfterCompensation(NOW.plusSeconds(3));

    assertThat(bet.riskCommitObserved()).isTrue();
    assertThat(bet.compensationState()).isEqualTo(CompensationState.COMPLETED);
    assertThat(bet.status()).isEqualTo(BetStatus.REJECTED);
  }

  @Test
  void settlesAgainstOriginalSystemUnitStake() {
    Bet bet = accepted(new BetSlipType.System(2, 3), List.of(leg("2"), leg("3"), leg("4")));
    UUID eventId = bet.legs().get(0).eventId();

    bet.settleBase(
        eventId,
        SettlementResult.WON,
        Money.krw(1_000),
        Money.krw(2_600),
        NOW.plusSeconds(10),
        "1".repeat(64));

    assertThat(bet.status()).isEqualTo(BetStatus.SETTLED);
    assertThat(bet.settlementResult()).isEqualTo(SettlementResult.WON);
    assertThat(bet.settledPayout()).isEqualTo(Money.krw(2_600));
    assertThat(bet.resolutionRevisionNumber()).isZero();
  }

  @Test
  void projectsWholeSlipVoidSeparately() {
    Bet bet = accepted(new BetSlipType.Single(), List.of(leg("2")));

    bet.voidBase(bet.legs().get(0).eventId(), VoidReason.EVENT_CANCELLED, NOW, "2".repeat(64));

    assertThat(bet.status()).isEqualTo(BetStatus.VOIDED);
    assertThat(bet.voidReason()).isEqualTo(VoidReason.EVENT_CANCELLED);
  }

  @Test
  void rejectsBaseResolutionForAnUnselectedEvent() {
    Bet bet = accepted(new BetSlipType.Single(), List.of(leg("2")));

    assertThatThrownBy(
            () ->
                bet.settleBase(
                    UUID.randomUUID(),
                    SettlementResult.WON,
                    Money.krw(1_000),
                    Money.krw(2_000),
                    NOW,
                    "a".repeat(64)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("selected leg");
  }
  static Bet accepted(BetSlipType type, List<BetLeg> legs) {
    Bet bet = Bet.pending(draft(UUID.randomUUID(), type), legs);
    bet.recordRiskReservation(NOW.plusSeconds(120), "9".repeat(64), false, NOW);
    bet.confirmWallet(UUID.randomUUID(), NOW);
    bet.commitRisk(NOW);
    bet.accept(NOW);
    return bet;
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
