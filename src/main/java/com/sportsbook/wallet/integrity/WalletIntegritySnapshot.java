package com.sportsbook.wallet.integrity;

import java.time.Instant;
import java.util.Objects;

/** Immutable, identifier-free summary of the last completed integrity scan. */
public record WalletIntegritySnapshot(
    Instant lastCheckedAt,
    long accountSnapshotDrift,
    long orphanAccountLedgers,
    long operationGroupDrift,
    long recoveryQueueDrift,
    long adjustmentOutcomeDrift,
    long adjustmentFailureDrift,
    long adjustmentFingerprintDrift,
    long adjustmentLedgerDrift) {

  public WalletIntegritySnapshot {
    Objects.requireNonNull(lastCheckedAt, "lastCheckedAt");
    if (accountSnapshotDrift < 0
        || orphanAccountLedgers < 0
        || operationGroupDrift < 0
        || recoveryQueueDrift < 0
        || adjustmentOutcomeDrift < 0
        || adjustmentFailureDrift < 0
        || adjustmentFingerprintDrift < 0
        || adjustmentLedgerDrift < 0) {
      throw new IllegalArgumentException("integrity drift counts cannot be negative");
    }
  }

  public long totalDrift() {
    return accountSnapshotDrift
        + orphanAccountLedgers
        + operationGroupDrift
        + recoveryQueueDrift
        + adjustmentOutcomeDrift
        + adjustmentFailureDrift
        + adjustmentFingerprintDrift
        + adjustmentLedgerDrift;
  }

  public boolean hasDrift() {
    return totalDrift() > 0;
  }
}
