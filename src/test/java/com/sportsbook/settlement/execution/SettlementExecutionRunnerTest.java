package com.sportsbook.settlement.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.protocol.value.Money;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SettlementExecutionRunnerTest {

  @Test
  void isolatesFailedBetAndContinuesIndependentExecution() {
    SettlementWalletExecutor wallet = mock(SettlementWalletExecutor.class);
    SettlementFinalizer finalizer = mock(SettlementFinalizer.class);
    SettlementAttemptRepository attempts = mock(SettlementAttemptRepository.class);
    Instant now = Instant.parse("2026-08-22T00:00:00Z");
    SettlementExecutionRunner runner =
        new SettlementExecutionRunner(
            wallet, finalizer, attempts, Clock.fixed(now, ZoneOffset.UTC));
    SettlementExecution first = execution();
    SettlementExecution second = execution();
    RuntimeException failure = new RuntimeException("dependency failed");
    doThrow(failure).when(wallet).releaseLocked(first.attempt(), first.userId());
    when(finalizer.settle(second.attempt())).thenReturn(true);

    SettlementExecutionRunner.BatchResult result = runner.fanOut(List.of(first, second));

    assertThat(result).isEqualTo(new SettlementExecutionRunner.BatchResult(1, 1));
    verify(attempts).releaseForRecovery(first.attempt(), failure, now);
    verify(wallet).releaseLocked(second.attempt(), second.userId());
    verify(wallet).forfeitLocked(second.attempt(), second.userId());
    verify(wallet).payHouseProfit(second.attempt(), second.userId());
    verify(finalizer).settle(second.attempt());
  }

  private static SettlementExecution execution() {
    SettlementAttempt attempt =
        SettlementAttempt.resolved(
            UUID.randomUUID(),
            UUID.randomUUID(),
            SettlementResult.WON,
            new SettlementMoneyPlan(
                Money.krw(1000), Money.krw(2000), Money.krw(1000), Money.krw(0), Money.krw(1000)),
            new SettlementLease(UUID.randomUUID(), Instant.MAX),
            Instant.EPOCH);
    return new SettlementExecution(attempt, UUID.randomUUID());
  }
}
