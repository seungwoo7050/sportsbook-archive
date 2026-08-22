package com.sportsbook.settlement.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(
    prefix = "settlement.workers",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class SettlementWorkerConfiguration {

  public static final String OUTBOX = "settlementOutboxScheduler";
  public static final String LIFECYCLE = "settlementLifecycleScheduler";
  public static final String RECOVERY = "settlementRecoveryScheduler";
  public static final String REVISION_RECOVERY = "settlementRevisionRecoveryScheduler";
  public static final String CORRECTION = "settlementCorrectionScheduler";

  @Bean(OUTBOX)
  ThreadPoolTaskScheduler outboxScheduler() {
    return worker("settlement-outbox-");
  }

  @Bean(LIFECYCLE)
  ThreadPoolTaskScheduler lifecycleScheduler() {
    return worker("settlement-lifecycle-");
  }

  @Bean(RECOVERY)
  ThreadPoolTaskScheduler recoveryScheduler() {
    return worker("settlement-recovery-");
  }

  @Bean(REVISION_RECOVERY)
  ThreadPoolTaskScheduler revisionRecoveryScheduler() {
    return worker("settlement-revision-recovery-");
  }

  @Bean(CORRECTION)
  ThreadPoolTaskScheduler correctionScheduler() {
    return worker("settlement-correction-");
  }

  private static ThreadPoolTaskScheduler worker(String threadPrefix) {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(1);
    scheduler.setThreadNamePrefix(threadPrefix);
    scheduler.setWaitForTasksToCompleteOnShutdown(true);
    scheduler.setAwaitTerminationSeconds(10);
    scheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    scheduler.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
    scheduler.setRemoveOnCancelPolicy(true);
    return scheduler;
  }
}
