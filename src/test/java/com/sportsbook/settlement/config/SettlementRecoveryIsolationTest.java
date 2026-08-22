package com.sportsbook.settlement.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class SettlementRecoveryIsolationTest {

  @Test
  void blockedWalletWorkersCannotStarveCorrectionCatchup() throws Exception {
    SettlementWorkerConfiguration configuration = new SettlementWorkerConfiguration();
    var base = configuration.recoveryScheduler();
    var revision = configuration.revisionRecoveryScheduler();
    var correction = configuration.correctionScheduler();
    base.initialize();
    revision.initialize();
    correction.initialize();
    CountDownLatch release = new CountDownLatch(1);
    CountDownLatch blocked = new CountDownLatch(2);
    CountDownLatch catchupRan = new CountDownLatch(1);

    try {
      base.execute(() -> block(blocked, release));
      revision.execute(() -> block(blocked, release));
      assertThat(blocked.await(1, TimeUnit.SECONDS)).isTrue();

      correction.execute(catchupRan::countDown);

      assertThat(catchupRan.await(1, TimeUnit.SECONDS)).isTrue();
    } finally {
      release.countDown();
      base.shutdown();
      revision.shutdown();
      correction.shutdown();
    }
  }

  private static void block(CountDownLatch started, CountDownLatch release) {
    started.countDown();
    try {
      release.await();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
    }
  }
}
