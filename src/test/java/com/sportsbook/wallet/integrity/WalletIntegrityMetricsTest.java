package com.sportsbook.wallet.integrity;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class WalletIntegrityMetricsTest {

  @Test
  void cachesBoundedScanMetricsWithoutIdentifierTags() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    WalletIntegrityMetrics metrics = new WalletIntegrityMetrics(registry);
    Instant checkedAt = Instant.parse("2026-08-21T12:00:00Z");
    WalletIntegritySnapshot drift = new WalletIntegritySnapshot(checkedAt, 1, 2, 3, 4, 5, 6, 7, 8);

    assertThat(gauge(registry, "wallet.integrity.total.drift")).isZero();

    metrics.record(drift);

    assertThat(gauge(registry, "wallet.integrity.account.snapshot.drift")).isEqualTo(1);
    assertThat(gauge(registry, "wallet.integrity.account.orphan.ledgers")).isEqualTo(2);
    assertThat(gauge(registry, "wallet.integrity.operation.group.drift")).isEqualTo(3);
    assertThat(gauge(registry, "wallet.integrity.recovery.queue.drift")).isEqualTo(4);
    assertThat(gauge(registry, "wallet.integrity.adjustment.outcome.drift")).isEqualTo(5);
    assertThat(gauge(registry, "wallet.integrity.adjustment.failure.drift")).isEqualTo(6);
    assertThat(gauge(registry, "wallet.integrity.adjustment.fingerprint.drift")).isEqualTo(7);
    assertThat(gauge(registry, "wallet.integrity.adjustment.ledger.drift")).isEqualTo(8);
    assertThat(gauge(registry, "wallet.integrity.total.drift")).isEqualTo(36);
    assertThat(gauge(registry, "wallet.integrity.last.checked.epoch.seconds"))
        .isEqualTo(checkedAt.getEpochSecond());
    assertThat(registry.getMeters())
        .allSatisfy(meter -> assertThat(meter.getId().getTags()).isEmpty());

    metrics.recordFailure();
    assertThat(gauge(registry, "wallet.integrity.scan.failed")).isEqualTo(1);
    assertThat(gauge(registry, "wallet.integrity.total.drift")).isEqualTo(36);

    metrics.record(new WalletIntegritySnapshot(checkedAt.plusSeconds(1), 0, 0, 0, 0, 0, 0, 0, 0));
    assertThat(gauge(registry, "wallet.integrity.scan.failed")).isZero();
    assertThat(gauge(registry, "wallet.integrity.total.drift")).isZero();
  }

  private double gauge(SimpleMeterRegistry registry, String name) {
    return registry.get(name).gauge().value();
  }
}
