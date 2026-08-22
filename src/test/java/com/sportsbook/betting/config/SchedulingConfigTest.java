package com.sportsbook.betting.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class SchedulingConfigTest {

  @Test
  void isolatesOutboxAndReconciliationWorkers() {
    SchedulingConfig config = new SchedulingConfig();
    ThreadPoolTaskScheduler outbox = config.outboxTaskScheduler();
    ThreadPoolTaskScheduler reconciliation = config.reconciliationTaskScheduler();
    try {
      outbox.initialize();
      reconciliation.initialize();

      assertThat(outbox).isNotSameAs(reconciliation);
      assertThat(outbox.getScheduledThreadPoolExecutor().getCorePoolSize()).isOne();
      assertThat(reconciliation.getScheduledThreadPoolExecutor().getCorePoolSize()).isOne();
      assertThat(outbox.getThreadNamePrefix()).isEqualTo("betting-outbox-");
      assertThat(reconciliation.getThreadNamePrefix()).isEqualTo("betting-reconciliation-");
    } finally {
      outbox.shutdown();
      reconciliation.shutdown();
    }
  }
}
