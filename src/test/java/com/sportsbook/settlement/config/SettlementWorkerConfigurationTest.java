package com.sportsbook.settlement.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class SettlementWorkerConfigurationTest {

  @Test
  void disablesEveryWorkerForIsolatedTestContexts() {
    new ApplicationContextRunner()
        .withUserConfiguration(SettlementWorkerConfiguration.class)
        .withPropertyValues("settlement.workers.enabled=false")
        .run(
            context ->
                assertThat(context)
                    .doesNotHaveBean(SettlementWorkerConfiguration.OUTBOX)
                    .doesNotHaveBean(SettlementWorkerConfiguration.LIFECYCLE)
                    .doesNotHaveBean(SettlementWorkerConfiguration.RECOVERY)
                    .doesNotHaveBean(SettlementWorkerConfiguration.REVISION_RECOVERY)
                    .doesNotHaveBean(SettlementWorkerConfiguration.CORRECTION));
  }

  @Test
  void createsIndependentSchedulersForEachWorkerClass() {
    new ApplicationContextRunner()
        .withUserConfiguration(SettlementWorkerConfiguration.class)
        .run(
            context ->
                assertThat(
                        List.of(
                            context.getBean(SettlementWorkerConfiguration.OUTBOX),
                            context.getBean(SettlementWorkerConfiguration.LIFECYCLE),
                            context.getBean(SettlementWorkerConfiguration.RECOVERY),
                            context.getBean(SettlementWorkerConfiguration.REVISION_RECOVERY),
                            context.getBean(SettlementWorkerConfiguration.CORRECTION)))
                    .doesNotHaveDuplicates());
  }

  @Test
  void aBlockedOutboxWorkerCannotStarveLifecycleWork() throws Exception {
    SettlementWorkerConfiguration configuration = new SettlementWorkerConfiguration();
    var outbox = configuration.outboxScheduler();
    var lifecycle = configuration.lifecycleScheduler();
    outbox.initialize();
    lifecycle.initialize();
    CountDownLatch outboxStarted = new CountDownLatch(1);
    CountDownLatch releaseOutbox = new CountDownLatch(1);
    CountDownLatch lifecycleRan = new CountDownLatch(1);

    try {
      outbox.execute(
          () -> {
            outboxStarted.countDown();
            await(releaseOutbox);
          });
      assertThat(outboxStarted.await(1, TimeUnit.SECONDS)).isTrue();

      lifecycle.execute(lifecycleRan::countDown);

      assertThat(lifecycleRan.await(1, TimeUnit.SECONDS)).isTrue();
    } finally {
      releaseOutbox.countDown();
      outbox.shutdown();
      lifecycle.shutdown();
    }
  }

  @Test
  void cancelsDelayedAndPeriodicWorkDuringShutdown() {
    var scheduler = new SettlementWorkerConfiguration().recoveryScheduler();
    scheduler.initialize();

    try {
      var executor = scheduler.getScheduledThreadPoolExecutor();
      assertThat(executor.getExecuteExistingDelayedTasksAfterShutdownPolicy()).isFalse();
      assertThat(executor.getContinueExistingPeriodicTasksAfterShutdownPolicy()).isFalse();
      assertThat(executor.getRemoveOnCancelPolicy()).isTrue();
    } finally {
      scheduler.shutdown();
    }
  }

  private static void await(CountDownLatch latch) {
    try {
      latch.await();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new AssertionError(exception);
    }
  }
}
