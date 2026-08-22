package com.sportsbook.settlement.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sportsbook.protocol.domain.BetSlipType;
import com.sportsbook.protocol.event.EventLifecycleStatus;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.settlement.config.SettlementRuntimeProperties;
import com.sportsbook.settlement.domain.Bet;
import com.sportsbook.settlement.execution.SettlementAttempt;
import com.sportsbook.settlement.execution.SettlementAttemptRepository;
import com.sportsbook.settlement.execution.SettlementExecution;
import com.sportsbook.settlement.execution.SettlementExecutionRunner;
import com.sportsbook.settlement.persistence.BetRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LifecycleFanoutTest {

  @Test
  @SuppressWarnings("unchecked")
  void claimsFullSystemExposureForEveryPendingBet() {
    BetRepository bets = mock(BetRepository.class);
    SettlementAttemptRepository attempts = mock(SettlementAttemptRepository.class);
    SettlementExecutionRunner runner = mock(SettlementExecutionRunner.class);
    Instant now = Instant.parse("2026-08-22T00:00:00Z");
    LifecycleFanout fanout =
        new LifecycleFanout(
            bets,
            attempts,
            runner,
            new SettlementRuntimeProperties(null, null, null, 0),
            Clock.fixed(now, ZoneOffset.UTC));
    UUID eventId = UUID.randomUUID();
    UUID betId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    Bet bet = mock(Bet.class);
    when(bet.betId()).thenReturn(betId);
    when(bet.userId()).thenReturn(userId);
    when(bet.stake()).thenReturn(Money.krw(1000));
    when(bet.slipType()).thenReturn(new BetSlipType.System(2, 3));
    when(bets.findPendingIdsByEvent(eventId)).thenReturn(List.of(betId));
    when(bets.findWithSelectionsById(betId)).thenReturn(Optional.of(bet));
    when(attempts.claimPending(any())).thenReturn(true);
    when(runner.fanOut(any())).thenReturn(new SettlementExecutionRunner.BatchResult(1, 0));

    LifecycleObservation tombstone =
        LifecycleObservation.observe(eventId, EventLifecycleStatus.CANCELLED, now, null, now);
    assertThat(fanout.fanOut(tombstone)).isEqualTo(new SettlementExecutionRunner.BatchResult(1, 0));

    ArgumentCaptor<SettlementAttempt> claim = ArgumentCaptor.forClass(SettlementAttempt.class);
    verify(attempts).claimPending(claim.capture());
    assertThat(claim.getValue().money().committed()).isEqualTo(Money.krw(3000));
    assertThat(claim.getValue().voidReason()).isEqualTo("EVENT_CANCELLED");
    verify(runner).fanOut((List<SettlementExecution>) any(List.class));
  }
}
