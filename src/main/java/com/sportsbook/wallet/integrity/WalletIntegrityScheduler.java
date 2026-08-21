package com.sportsbook.wallet.integrity;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodically refreshes the cached integrity view without hiding database failures. */
@Component
@ConditionalOnProperty(name = "wallet.integrity.scheduling-enabled", havingValue = "true")
public class WalletIntegrityScheduler {

  private final WalletIntegrityScanner scanner;
  private final WalletIntegrityMetrics metrics;

  public WalletIntegrityScheduler(WalletIntegrityScanner scanner, WalletIntegrityMetrics metrics) {
    this.scanner = scanner;
    this.metrics = metrics;
  }

  @Scheduled(fixedDelayString = "${wallet.integrity.poll-interval:PT30S}")
  public void scan() {
    try {
      metrics.record(scanner.scan());
    } catch (RuntimeException failure) {
      metrics.recordFailure();
      throw failure;
    }
  }
}
