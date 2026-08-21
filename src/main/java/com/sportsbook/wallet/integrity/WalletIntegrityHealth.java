package com.sportsbook.wallet.integrity;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/** Degrades health only for detected drift or a failed integrity scan. */
@Component
public class WalletIntegrityHealth implements HealthIndicator {

  private final WalletIntegrityMetrics metrics;

  public WalletIntegrityHealth(WalletIntegrityMetrics metrics) {
    this.metrics = metrics;
  }

  @Override
  public Health health() {
    WalletIntegrityMetrics.Status status = metrics.status();
    if (status.scanFailed()) {
      return Health.down().withDetail("reason", "integrity_scan_failed").build();
    }
    if (status.snapshot() == null) {
      return Health.unknown().withDetail("reason", "integrity_not_checked").build();
    }
    WalletIntegritySnapshot snapshot = status.snapshot();
    Health.Builder health = snapshot.hasDrift() ? Health.down() : Health.up();
    return health
        .withDetail("lastCheckedAt", snapshot.lastCheckedAt())
        .withDetail("driftCount", snapshot.totalDrift())
        .build();
  }
}
