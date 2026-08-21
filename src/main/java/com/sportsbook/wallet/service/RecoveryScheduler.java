package com.sportsbook.wallet.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodically gives one due FIFO head its own bounded recovery transaction. */
@Component
@ConditionalOnProperty(name = "wallet.recovery.scheduling-enabled", havingValue = "true")
public class RecoveryScheduler {
  private final RecoveryWorker worker;

  public RecoveryScheduler(RecoveryWorker worker) {
    this.worker = worker;
  }

  @Scheduled(fixedDelayString = "${wallet.recovery.poll-interval:PT1S}")
  public void poll() {
    worker.recoverOne();
  }
}
