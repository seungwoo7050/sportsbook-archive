package com.sportsbook.settlement.result;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.settlement.execution.SettlementAttempt;
import com.sportsbook.settlement.execution.SettlementExecution;
import com.sportsbook.settlement.execution.SettlementExecutionRunner;
import com.sportsbook.settlement.execution.SettlementLease;
import com.sportsbook.settlement.execution.SettlementMoneyPlan;
import com.sportsbook.settlement.observability.SettlementMetrics;
import com.sportsbook.settlement.persistence.BetRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ResultFanoutTest {

  @Test
  void preparesEveryActionableBetBeforeExecutingTheClaimedBatch() {
    BetRepository bets = mock(BetRepository.class);
    ResultSettlementPreparer preparer = mock(ResultSettlementPreparer.class);
    SettlementExecutionRunner runner = mock(SettlementExecutionRunner.class);
    UUID eventId = UUID.randomUUID();
    UUID first = UUID.randomUUID();
    UUID partial = UUID.randomUUID();
    AcceptedResult accepted =
        new AcceptedResult(
            eventId, UUID.randomUUID(), MatchOutcomeMode.VOIDED, Map.of(), Instant.EPOCH);
    SettlementMoneyPlan money =
        new SettlementMoneyPlan(
            Money.krw(100), Money.krw(100), Money.krw(100), Money.krw(0), Money.krw(0));
    SettlementAttempt attempt =
        SettlementAttempt.resolved(
            first,
            eventId,
            SettlementResult.VOID,
            money,
            SettlementLease.acquire(Instant.EPOCH, Duration.ofSeconds(30)),
            Instant.EPOCH);
    SettlementExecution execution = new SettlementExecution(attempt, UUID.randomUUID());
    when(bets.findResultActionableIdsByEvent(eventId)).thenReturn(List.of(first, partial));
    when(preparer.prepare(first, accepted)).thenReturn(Optional.of(execution));
    when(preparer.prepare(partial, accepted)).thenReturn(Optional.empty());
    var expected = new SettlementExecutionRunner.BatchResult(1, 0);
    when(runner.fanOut(List.of(execution))).thenReturn(expected);
    SimpleMeterRegistry registry = new SimpleMeterRegistry();

    assertThat(
            new ResultFanout(bets, preparer, runner, new SettlementMetrics(registry))
                .fanOut(accepted))
        .isEqualTo(expected);

    verify(preparer).prepare(first, accepted);
    verify(preparer).prepare(partial, accepted);
    verify(runner).fanOut(List.of(execution));
    assertThat(
            registry
                .counter(
                    SettlementMetrics.OPERATIONS, "flow", "base_result", "outcome", "succeeded")
                .count())
        .isOne();
    assertThat(registry.timer(SettlementMetrics.DURATION, "flow", "base_result").count()).isOne();
  }
}
