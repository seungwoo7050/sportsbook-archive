package com.sportsbook.settlement.correction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sportsbook.protocol.domain.BetSlipType;
import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.Odds;
import com.sportsbook.settlement.config.SettlementTopics;
import com.sportsbook.settlement.domain.Bet;
import com.sportsbook.settlement.domain.BetSelection;
import com.sportsbook.settlement.domain.EmbeddedMoney;
import com.sportsbook.settlement.domain.SlipKind;
import com.sportsbook.settlement.outbox.OutboxEvent;
import com.sportsbook.settlement.outbox.OutboxEventRepository;
import com.sportsbook.settlement.persistence.BetRepository;
import com.sportsbook.settlement.resolver.ResolvedSelection;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RevisionFinalizerTest {

  @Test
  void commitsZeroDeltaStateSelectionSourceAndOutboxTogether() {
    BetRepository bets = mock(BetRepository.class);
    RevisionPlanRepository revisions = mock(RevisionPlanRepository.class);
    OutboxEventRepository outbox = mock(OutboxEventRepository.class);
    Bet bet = settledBet();
    RevisionPlan plan = plan(bet);
    RevisionLease lease = new RevisionLease(UUID.randomUUID(), Instant.MAX);
    Instant now = Instant.EPOCH.plusSeconds(2);
    when(bets.findForUpdateById(bet.betId())).thenReturn(Optional.of(bet));
    when(revisions.markApplied(plan.revisionId(), lease, null, now)).thenReturn(true);
    RevisionFinalizer finalizer =
        new RevisionFinalizer(
            bets, revisions, outbox, new SettlementTopics(null, null, null, null, null, null));

    assertThat(finalizer.apply(plan, lease, null, now)).isTrue();

    assertThat(bet.revisionNumber()).isOne();
    assertThat(bet.result()).isEqualTo(SettlementResult.PUSH);
    assertThat(bet.selections().get(0).sourceCandidateId())
        .isEqualTo(plan.target().sourceCandidateId());
    verify(revisions).markApplied(plan.revisionId(), lease, null, now);
    verify(outbox).save(any(OutboxEvent.class));
  }

  private static Bet settledBet() {
    BetSelection selection =
        new BetSelection(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Odds.ofDecimal("2.0000"));
    selection.applyCandidate(UUID.randomUUID(), SettlementResult.WON);
    Bet bet =
        Bet.pending(
            UUID.randomUUID(),
            UUID.randomUUID(),
            SlipKind.SINGLE,
            null,
            null,
            EmbeddedMoney.of(Money.krw(100)),
            Instant.EPOCH,
            List.of(selection),
            Instant.EPOCH);
    bet.recordSettled(SettlementResult.WON, Money.krw(100), Instant.EPOCH);
    return bet;
  }

  private static RevisionPlan plan(Bet bet) {
    RevisionTarget target =
        new RevisionTarget(
            bet.betId(),
            1,
            bet.userId(),
            bet.selections().get(0).eventId(),
            UUID.randomUUID(),
            bet.result(),
            bet.payout(),
            new BetSlipType.Single(),
            bet.stake(),
            List.of(
                new ResolvedSelection(
                    bet.selections().get(0).selectionId(),
                    bet.selections().get(0).odds(),
                    SettlementResult.PUSH)),
            Instant.EPOCH.plusSeconds(1));
    return new RevisionPlan(
        UUID.randomUUID(), target, SettlementResult.PUSH, Money.krw(100), Instant.EPOCH);
  }
}
