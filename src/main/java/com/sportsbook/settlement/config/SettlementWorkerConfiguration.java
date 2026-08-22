package com.sportsbook.settlement.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class SettlementWorkerConfiguration {

  public static final String OUTBOX = "settlementOutboxScheduler";
  public static final String LIFECYCLE = "settlementLifecycleScheduler";
  public static final String RECOVERY = "settlementRecoveryScheduler";

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

  private static ThreadPoolTaskScheduler worker(String threadPrefix) {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(1);
    scheduler.setThreadNamePrefix(threadPrefix);
    scheduler.setWaitForTasksToCompleteOnShutdown(true);
    scheduler.setAwaitTerminationSeconds(10);
    return scheduler;
  }
}
