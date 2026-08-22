package com.sportsbook.betting.placement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sportsbook.betting.domain.Bet;
import com.sportsbook.betting.domain.BetDraft;
import com.sportsbook.betting.domain.BetLeg;
import com.sportsbook.betting.domain.CompensationState;
import com.sportsbook.betting.domain.PlacementPhase;
import com.sportsbook.betting.outbox.OutboxEventRepository;
import com.sportsbook.betting.persistence.BetRepository;
import com.sportsbook.betting.persistence.PlacementRequestRepository;
import com.sportsbook.protocol.domain.BetSlipType;
import com.sportsbook.protocol.domain.BetStatus;
import com.sportsbook.protocol.error.ErrorCode;
import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.Odds;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BetStoreReplayTest {

  @Test
  void repeatsRiskCommitWithoutRegressingItsCheckpoint() {
    Bet bet = reserved();
    bet.confirmWallet(UUID.randomUUID(), Instant.EPOCH);
    BetStore store = store(bet);

    store.commitRisk(bet.betId(), Instant.EPOCH);
    store.commitRisk(bet.betId(), Instant.EPOCH.plusSeconds(1));

    assertThat(bet.placementPhase()).isEqualTo(PlacementPhase.RISK_COMMITTED);
  }

  @Test
  void repeatsEveryRiskReleaseCheckpointUntilTerminal() {
    Bet bet = reserved();
    BetStore store = store(bet);

    store.requireRiskRelease(bet.betId(), ErrorCode.VALIDATION_FAILED, "reject", Instant.EPOCH);
    store.requireRiskRelease(bet.betId(), ErrorCode.VALIDATION_FAILED, "reject", Instant.EPOCH);
    store.beginCompensation(bet.betId(), Instant.EPOCH);
    store.beginCompensation(bet.betId(), Instant.EPOCH);
    store.completeRiskRelease(bet.betId(), false, Instant.EPOCH);
    store.completeRiskRelease(bet.betId(), false, Instant.EPOCH);
    store.rejectAfterCompensation(bet.betId(), Instant.EPOCH);
    store.rejectAfterCompensation(bet.betId(), Instant.EPOCH);

    assertThat(bet.compensationState()).isEqualTo(CompensationState.COMPLETED);
    assertThat(bet.status()).isEqualTo(BetStatus.REJECTED);
  }

  private static BetStore store(Bet bet) {
    BetRepository bets = mock(BetRepository.class);
    when(bets.findLockedByBetId(bet.betId())).thenReturn(Optional.of(bet));
    return new BetStore(
        bets, mock(OutboxEventRepository.class), mock(PlacementRequestRepository.class));
  }

  private static Bet reserved() {
    UUID betId = UUID.randomUUID();
    Bet bet =
        Bet.pending(
            new BetDraft(
                betId,
                UUID.randomUUID(),
                "B-2026-08-22-REPLAY",
                new BetSlipType.Single(),
                Money.krw(1_000),
                Money.krw(2_000),
                IdempotencyKey.of("replay-" + betId),
                "a".repeat(64),
                Instant.EPOCH),
            List.of(
                BetLeg.create(
                    UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Odds.ofDecimal("2"))));
    bet.recordRiskReservation(Instant.EPOCH.plusSeconds(30), "b".repeat(64), false, Instant.EPOCH);
    return bet;
  }
}
