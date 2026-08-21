package com.sportsbook.wallet.integrity;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;

class WalletIntegrityHealthTest {

  private static final Instant CHECKED_AT = Instant.parse("2026-08-21T13:00:00Z");

  @Test
  void degradesOnlyForDriftOrScanFailure() {
    WalletIntegrityMetrics metrics = new WalletIntegrityMetrics(new SimpleMeterRegistry());
    WalletIntegrityHealth health = new WalletIntegrityHealth(metrics);

    assertThat(health.health().getStatus()).isEqualTo(Status.UNKNOWN);

    metrics.record(new WalletIntegritySnapshot(CHECKED_AT, 0, 0, 0, 0, 0, 0, 0, 0));
    assertThat(health.health().getStatus()).isEqualTo(Status.UP);

    metrics.record(new WalletIntegritySnapshot(CHECKED_AT, 0, 0, 0, 0, 0, 2, 0, 0));
    assertThat(health.health().getStatus()).isEqualTo(Status.DOWN);
    assertThat(health.health().getDetails()).containsEntry("driftCount", 2L);

    metrics.recordFailure();
    assertThat(health.health().getStatus()).isEqualTo(Status.DOWN);
    assertThat(health.health().getDetails())
        .containsExactlyInAnyOrderEntriesOf(java.util.Map.of("reason", "integrity_scan_failed"));

    metrics.record(new WalletIntegritySnapshot(CHECKED_AT.plusSeconds(1), 0, 0, 0, 0, 0, 0, 0, 0));
    assertThat(health.health().getStatus()).isEqualTo(Status.UP);
  }
}
