package com.sportsbook.betting.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class SchedulingConfig {

  @Bean(name = "outboxTaskScheduler", destroyMethod = "shutdown")
  ThreadPoolTaskScheduler outboxTaskScheduler() {
    return scheduler("betting-outbox-");
  }

  @Bean(name = "reconciliationTaskScheduler", destroyMethod = "shutdown")
  ThreadPoolTaskScheduler reconciliationTaskScheduler() {
    return scheduler("betting-reconciliation-");
  }

  private static ThreadPoolTaskScheduler scheduler(String prefix) {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(1);
    scheduler.setThreadNamePrefix(prefix);
    scheduler.setWaitForTasksToCompleteOnShutdown(true);
    scheduler.setAwaitTerminationSeconds(10);
    return scheduler;
  }
}
