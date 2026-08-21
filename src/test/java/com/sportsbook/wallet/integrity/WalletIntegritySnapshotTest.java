package com.sportsbook.wallet.integrity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class WalletIntegritySnapshotTest {

  private static final Instant CHECKED_AT = Instant.parse("2026-08-21T10:00:00Z");

  @Test
  void summarizesBoundedDriftCountsWithoutIdentifiers() {
    WalletIntegritySnapshot clean = new WalletIntegritySnapshot(CHECKED_AT, 0, 0, 0, 0, 0, 0, 0, 0);
    WalletIntegritySnapshot drift = new WalletIntegritySnapshot(CHECKED_AT, 1, 2, 3, 4, 5, 6, 7, 8);

    assertThat(clean.hasDrift()).isFalse();
    assertThat(drift.hasDrift()).isTrue();
    assertThat(drift.totalDrift()).isEqualTo(36);
    assertThat(drift.lastCheckedAt()).isEqualTo(CHECKED_AT);
  }

  @Test
  void rejectsNegativeCounts() {
    assertThatThrownBy(() -> new WalletIntegritySnapshot(CHECKED_AT, 0, 0, -1, 0, 0, 0, 0, 0))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
