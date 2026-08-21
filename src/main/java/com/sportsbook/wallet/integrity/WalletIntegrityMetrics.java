package com.sportsbook.wallet.integrity;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.ToDoubleFunction;
import org.springframework.stereotype.Component;

/** Publishes bounded, scrape-safe gauges from the last completed integrity scan. */
@Component
public class WalletIntegrityMetrics {

  private static final Map<String, ToDoubleFunction<WalletIntegritySnapshot>> DRIFT_GAUGES =
      Map.of(
          "wallet.integrity.account.snapshot.drift", WalletIntegritySnapshot::accountSnapshotDrift,
          "wallet.integrity.account.orphan.ledgers", WalletIntegritySnapshot::orphanAccountLedgers,
          "wallet.integrity.operation.group.drift", WalletIntegritySnapshot::operationGroupDrift,
          "wallet.integrity.recovery.queue.drift", WalletIntegritySnapshot::recoveryQueueDrift,
          "wallet.integrity.adjustment.outcome.drift",
              WalletIntegritySnapshot::adjustmentOutcomeDrift,
          "wallet.integrity.adjustment.failure.drift",
              WalletIntegritySnapshot::adjustmentFailureDrift,
          "wallet.integrity.adjustment.fingerprint.drift",
              WalletIntegritySnapshot::adjustmentFingerprintDrift,
          "wallet.integrity.adjustment.ledger.drift",
              WalletIntegritySnapshot::adjustmentLedgerDrift,
          "wallet.integrity.total.drift", WalletIntegritySnapshot::totalDrift);

  private final AtomicReference<Status> state = new AtomicReference<>(new Status(null, false));

  public WalletIntegrityMetrics(MeterRegistry registry) {
    DRIFT_GAUGES.forEach((name, valueFunction) -> gauge(registry, name, valueFunction));
    Gauge.builder("wallet.integrity.scan.failed", state, value -> value.get().scanFailed() ? 1 : 0)
        .register(registry);
    Gauge.builder(
            "wallet.integrity.last.checked.epoch.seconds",
            state,
            value ->
                value.get().snapshot() == null
                    ? 0
                    : value.get().snapshot().lastCheckedAt().getEpochSecond())
        .register(registry);
  }

  public void record(WalletIntegritySnapshot snapshot) {
    state.set(new Status(Objects.requireNonNull(snapshot, "snapshot"), false));
  }

  public void recordFailure() {
    state.updateAndGet(previous -> new Status(previous.snapshot(), true));
  }

  Status status() {
    return state.get();
  }

  private void gauge(
      MeterRegistry registry,
      String name,
      ToDoubleFunction<WalletIntegritySnapshot> valueFunction) {
    Gauge.builder(name, state, value -> metric(value.get(), valueFunction)).register(registry);
  }

  private double metric(Status current, ToDoubleFunction<WalletIntegritySnapshot> valueFunction) {
    return current.snapshot() == null ? 0 : valueFunction.applyAsDouble(current.snapshot());
  }

  record Status(WalletIntegritySnapshot snapshot, boolean scanFailed) {}
}
