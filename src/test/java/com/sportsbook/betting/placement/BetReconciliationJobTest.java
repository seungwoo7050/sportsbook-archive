package com.sportsbook.betting.placement;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sportsbook.betting.persistence.BetRepository;
import java.lang.reflect.Constructor;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;

class BetReconciliationJobTest {

  @Test
  void reconcilesOnlyAnOwnedBoundedClaimAndClearsIt() {
    BetRepository bets = mock(BetRepository.class);
    BetPlacementService placement = mock(BetPlacementService.class);
    UUID betId = UUID.randomUUID();
    when(bets.claimReconciliationBatch("worker-1", 30_000, 20_000, 10_000, 100))
        .thenReturn(List.of(betId));
    when(bets.clearReconciliationClaim(betId, "worker-1")).thenReturn(1);

    new BetReconciliationJob(
            bets,
            placement,
            "worker-1",
            Duration.ofSeconds(30),
            Duration.ofSeconds(20),
            Duration.ofSeconds(10))
        .reconcile();

    verify(placement).reconcile(betId);
    verify(bets).clearReconciliationClaim(betId, "worker-1");
  }

  @Test
  void releasesItsClaimAfterAReplayFailure() {
    BetRepository bets = mock(BetRepository.class);
    BetPlacementService placement = mock(BetPlacementService.class);
    UUID betId = UUID.randomUUID();
    when(bets.claimReconciliationBatch("worker-2", 30_000, 20_000, 10_000, 100))
        .thenReturn(List.of(betId));
    when(bets.clearReconciliationClaim(betId, "worker-2")).thenReturn(1);
    org.mockito.Mockito.doThrow(new IllegalStateException("failure"))
        .when(placement)
        .reconcile(betId);

    new BetReconciliationJob(
            bets,
            placement,
            "worker-2",
            Duration.ofSeconds(30),
            Duration.ofSeconds(20),
            Duration.ofSeconds(10))
        .reconcile();

    verify(bets).clearReconciliationClaim(betId, "worker-2");
  }

  @Test
  void alwaysUsesAnInstanceUniqueOwner() {
    Constructor<?> constructor = BetReconciliationJob.class.getConstructors()[0];
    Value owner = constructor.getParameters()[2].getAnnotation(Value.class);

    org.assertj.core.api.Assertions.assertThat(owner.value()).isEqualTo("${random.uuid}");
  }

  @Test
  void runsOnTheDedicatedReconciliationScheduler() throws Exception {
    Scheduled scheduled =
        BetReconciliationJob.class.getMethod("reconcile").getAnnotation(Scheduled.class);

    org.assertj.core.api.Assertions.assertThat(scheduled.scheduler())
        .isEqualTo("reconciliationTaskScheduler");
  }
}
