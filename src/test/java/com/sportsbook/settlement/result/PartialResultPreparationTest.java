package com.sportsbook.settlement.result;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.Odds;
import com.sportsbook.settlement.config.SettlementRuntimeProperties;
import com.sportsbook.settlement.domain.Bet;
import com.sportsbook.settlement.domain.BetSelection;
import com.sportsbook.settlement.domain.EmbeddedMoney;
import com.sportsbook.settlement.domain.SlipKind;
import com.sportsbook.settlement.execution.SettlementAttemptRepository;
import com.sportsbook.settlement.persistence.BetRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PartialResultPreparationTest {

  @Test
  void persistsTheAcceptedSourceWithoutClaimingAnIncompleteMultiple() {
    UUID eventId = UUID.randomUUID();
    UUID candidateId = UUID.randomUUID();
    BetSelection resolved = selection(eventId);
    Bet bet = pending(List.of(resolved, selection(UUID.randomUUID())));
    BetRepository bets = mock(BetRepository.class);
    SettlementAttemptRepository attempts = mock(SettlementAttemptRepository.class);
    when(bets.findForUpdateById(bet.betId())).thenReturn(java.util.Optional.of(bet));
    AcceptedResult accepted =
        new AcceptedResult(
            eventId,
            candidateId,
            MatchOutcomeMode.COMPLETED,
            Map.of(resolved.selectionId(), SettlementResult.WON),
            Instant.EPOCH);

    assertThat(preparer(bets, attempts).prepare(bet.betId(), accepted)).isEmpty();

    assertThat(resolved.outcome()).isEqualTo(SettlementResult.WON);
    assertThat(resolved.sourceCandidateId()).isEqualTo(candidateId);
    verify(attempts, never()).claimPending(any(), any());
  }

  private static ResultSettlementPreparer preparer(
      BetRepository bets, SettlementAttemptRepository attempts) {
    return new ResultSettlementPreparer(
        bets,
        attempts,
        new BaseSettlementPlanner(),
        new SettlementRuntimeProperties(null, null, null, 0),
        Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
  }

  private static Bet pending(List<BetSelection> selections) {
    return Bet.pending(
        UUID.randomUUID(),
        UUID.randomUUID(),
        SlipKind.MULTIPLE,
        null,
        null,
        EmbeddedMoney.of(Money.krw(100)),
        Instant.EPOCH,
        selections,
        Instant.EPOCH);
  }

  private static BetSelection selection(UUID eventId) {
    return new BetSelection(
        eventId, UUID.randomUUID(), UUID.randomUUID(), Odds.ofDecimal("2.0000"));
  }
}
