package com.sportsbook.wallet.integrity;

import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Runs all durable wallet invariants against one repeatable database view. */
@Service
public class WalletIntegrityScanner {

  private final AccountIntegrityRepository accounts;
  private final OperationIntegrityRepository operations;
  private final RecoveryQueueIntegrityRepository recovery;
  private final AdjustmentOperationIntegrityRepository adjustmentOutcomes;
  private final AdjustmentFailureIntegrityRepository adjustmentFailures;
  private final AdjustmentFingerprintIntegrityRepository adjustmentFingerprints;
  private final AdjustmentLedgerIntegrityRepository adjustmentLedgers;
  private final Clock clock;

  public WalletIntegrityScanner(
      AccountIntegrityRepository accounts,
      OperationIntegrityRepository operations,
      RecoveryQueueIntegrityRepository recovery,
      AdjustmentOperationIntegrityRepository adjustmentOutcomes,
      AdjustmentFailureIntegrityRepository adjustmentFailures,
      AdjustmentFingerprintIntegrityRepository adjustmentFingerprints,
      AdjustmentLedgerIntegrityRepository adjustmentLedgers,
      Clock clock) {
    this.accounts = accounts;
    this.operations = operations;
    this.recovery = recovery;
    this.adjustmentOutcomes = adjustmentOutcomes;
    this.adjustmentFailures = adjustmentFailures;
    this.adjustmentFingerprints = adjustmentFingerprints;
    this.adjustmentLedgers = adjustmentLedgers;
    this.clock = clock;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public WalletIntegritySnapshot scan() {
    long accountSnapshotDrift = accounts.findSnapshotDrift().size();
    long orphanAccountLedgers = accounts.findOrphanLedgerAccountIds().size();
    long operationGroupDrift = operations.findGroupDriftKeys().size();
    long recoveryQueueDrift = recovery.findQueueDriftUsers().size();
    long adjustmentOutcomeDrift = adjustmentOutcomes.findOutcomeDriftKeys().size();
    long adjustmentFailureDrift = adjustmentFailures.findFailureDriftKeys().size();
    long adjustmentFingerprintDrift = adjustmentFingerprints.findFingerprintDriftKeys().size();
    long adjustmentLedgerDrift = adjustmentLedgers.findLedgerDriftKeys().size();
    return new WalletIntegritySnapshot(
        clock.instant(),
        accountSnapshotDrift,
        orphanAccountLedgers,
        operationGroupDrift,
        recoveryQueueDrift,
        adjustmentOutcomeDrift,
        adjustmentFailureDrift,
        adjustmentFingerprintDrift,
        adjustmentLedgerDrift);
  }
}
