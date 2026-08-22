package com.sportsbook.settlement.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.protocol.event.EventLifecycleStatus;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.settlement.execution.SettlementAttempt;
import com.sportsbook.settlement.execution.SettlementExecution;
import com.sportsbook.settlement.execution.SettlementExecutionRunner;
import com.sportsbook.settlement.execution.SettlementLease;
import com.sportsbook.settlement.execution.SettlementMoneyPlan;
import com.sportsbook.settlement.persistence.BetRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LifecycleFanoutTest {

  @Test
  @SuppressWarnings("unchecked")
  void preparesEveryPendingBetBeforeExecutingTheBatch() {
    BetRepository bets = mock(BetRepository.class);
    LifecycleSettlementPreparer preparer = mock(LifecycleSettlementPreparer.class);
    SettlementExecutionRunner runner = mock(SettlementExecutionRunner.class);
    Instant now = Instant.parse("2026-08-22T00:00:00Z");
    LifecycleFanout fanout = new LifecycleFanout(bets, preparer, runner);
    UUID eventId = UUID.randomUUID();
    UUID betId = UUID.randomUUID();
    SettlementMoneyPlan money =
        new SettlementMoneyPlan(
            Money.krw(100), Money.krw(100), Money.krw(100), Money.krw(0), Money.krw(0));
    SettlementAttempt attempt =
        SettlementAttempt.resolved(
            betId,
            eventId,
            SettlementResult.VOID,
            money,
            SettlementLease.acquire(now, Duration.ofSeconds(30)),
            now);
    SettlementExecution execution = new SettlementExecution(attempt, UUID.randomUUID());
    when(bets.findPendingIdsByEvent(eventId)).thenReturn(List.of(betId));
    when(preparer.prepare(betId, eventId, "EVENT_CANCELLED")).thenReturn(Optional.of(execution));
    when(runner.fanOut(any())).thenReturn(new SettlementExecutionRunner.BatchResult(1, 0));

    LifecycleObservation tombstone =
        LifecycleObservation.observe(eventId, EventLifecycleStatus.CANCELLED, now, null, now);
    assertThat(fanout.fanOut(tombstone)).isEqualTo(new SettlementExecutionRunner.BatchResult(1, 0));

    verify(preparer).prepare(betId, eventId, "EVENT_CANCELLED");
    verify(runner).fanOut((List<SettlementExecution>) any(List.class));
  }
}
