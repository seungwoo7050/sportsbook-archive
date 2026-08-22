package com.sportsbook.settlement.correction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sportsbook.protocol.domain.BetSlipType;
import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.Odds;
import com.sportsbook.settlement.config.SettlementRuntimeProperties;
import com.sportsbook.settlement.observability.SettlementMetrics;
import com.sportsbook.settlement.resolver.ResolvedSelection;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RevisionRecoveryScannerTest {

  @Test
  void rehydratesClaimsAndExecutesEveryRecoveryGetFirst() {
    RevisionRecoveryRepository recovery = mock(RevisionRecoveryRepository.class);
    RevisionPlanReader plans = mock(RevisionPlanReader.class);
    RevisionExecutionRunner runner = mock(RevisionExecutionRunner.class);
    SettlementRuntimeProperties runtime =
        new SettlementRuntimeProperties(null, Duration.ofSeconds(1), Duration.ofSeconds(30), 25);
    RevisionPlan ambiguous = plan();
    RevisionPlan blocked = plan();
    RevisionLease firstLease = new RevisionLease(UUID.randomUUID(), Instant.MAX);
    RevisionLease secondLease = new RevisionLease(UUID.randomUUID(), Instant.MAX);
    var first = new RevisionRecoveryRepository.Claim(ambiguous.revisionId(), firstLease, false);
    var second = new RevisionRecoveryRepository.Claim(blocked.revisionId(), secondLease, true);
    when(recovery.claimDue(Duration.ofSeconds(30), 25)).thenReturn(List.of(first, second));
    when(plans.find(ambiguous.revisionId())).thenReturn(Optional.of(ambiguous));
    when(plans.find(blocked.revisionId())).thenReturn(Optional.of(blocked));
    when(runner.execute(ambiguous, firstLease, true, true))
        .thenReturn(RevisionExecutionRunner.Result.APPLIED);
    when(runner.execute(blocked, secondLease, true, false))
        .thenReturn(RevisionExecutionRunner.Result.BLOCKED);
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    RevisionRecoveryScanner scanner =
        new RevisionRecoveryScanner(
            recovery, plans, runner, runtime, new SettlementMetrics(registry));

    assertThat(scanner.recover())
        .containsExactly(
            RevisionExecutionRunner.Result.APPLIED, RevisionExecutionRunner.Result.BLOCKED);
    var ordered = inOrder(recovery, plans, runner);
    ordered.verify(recovery).claimDue(Duration.ofSeconds(30), 25);
    ordered.verify(plans).find(ambiguous.revisionId());
    ordered.verify(runner).execute(ambiguous, firstLease, true, true);
    ordered.verify(plans).find(blocked.revisionId());
    ordered.verify(runner).execute(blocked, secondLease, true, false);
    assertThat(outcome(registry, "applied")).isOne();
    assertThat(outcome(registry, "blocked")).isOne();
    assertThat(registry.timer(SettlementMetrics.DURATION, "flow", "revision").count()).isOne();
  }

  private static double outcome(SimpleMeterRegistry registry, String outcome) {
    return registry
        .counter(SettlementMetrics.OPERATIONS, "flow", "revision", "outcome", outcome)
        .count();
  }

  private static RevisionPlan plan() {
    RevisionTarget target =
        new RevisionTarget(
            UUID.randomUUID(),
            1,
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            SettlementResult.WON,
            Money.krw(200),
            new BetSlipType.Single(),
            Money.krw(100),
            List.of(
                new ResolvedSelection(
                    UUID.randomUUID(), Odds.ofDecimal("2.0000"), SettlementResult.PUSH)),
            Instant.EPOCH);
    return new RevisionPlan(
        UUID.randomUUID(), target, SettlementResult.PUSH, Money.krw(100), Instant.EPOCH);
  }
}
