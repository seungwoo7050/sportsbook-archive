package com.sportsbook.settlement.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.settlement.config.SettlementRuntimeProperties;
import com.sportsbook.settlement.config.SettlementWorkerConfiguration;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

class SettlementAttemptRecoveryTest {

  @Test
  void claimsAndExecutesExpiredAttemptsAfterRestart() {
    Instant now = Instant.parse("2026-08-22T03:00:00Z");
    SettlementAttemptRepository attempts = mock(SettlementAttemptRepository.class);
    SettlementExecutionRunner runner = mock(SettlementExecutionRunner.class);
    SettlementRuntimeProperties runtime =
        new SettlementRuntimeProperties(null, Duration.ofSeconds(1), Duration.ofSeconds(30), 25);
    SettlementExecution execution =
        new SettlementExecution(attempt(now.minusSeconds(1)), UUID.randomUUID());
    when(attempts.claimRecoveryBatch(Duration.ofSeconds(30), 25)).thenReturn(List.of(execution));
    when(runner.fanOut(List.of(execution)))
        .thenReturn(new SettlementExecutionRunner.BatchResult(1, 0));
    SettlementAttemptRecovery recovery = new SettlementAttemptRecovery(attempts, runner, runtime);

    assertThat(recovery.recover()).isEqualTo(new SettlementExecutionRunner.BatchResult(1, 0));
    verify(attempts).claimRecoveryBatch(Duration.ofSeconds(30), 25);
    verify(runner).fanOut(List.of(execution));
  }

  @Test
  void runsOnTheIsolatedRecoveryScheduler() throws NoSuchMethodException {
    Scheduled scheduled =
        SettlementAttemptRecovery.class.getMethod("recover").getAnnotation(Scheduled.class);

    assertThat(scheduled.scheduler()).isEqualTo(SettlementWorkerConfiguration.RECOVERY);
  }

  private SettlementAttempt attempt(Instant leaseUntil) {
    return SettlementAttempt.resolved(
        UUID.randomUUID(),
        UUID.randomUUID(),
        SettlementResult.WON,
        new SettlementMoneyPlan(
            Money.krw(100), Money.krw(200), Money.krw(100), Money.krw(0), Money.krw(100)),
        new SettlementLease(UUID.randomUUID(), leaseUntil),
        Instant.EPOCH);
  }
}
