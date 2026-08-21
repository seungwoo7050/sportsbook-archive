package com.sportsbook.wallet.integrity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class WalletIntegrityScannerTest {

  @Test
  void combinesEveryInvariantIntoOneIdentifierFreeSnapshot() {
    AccountIntegrityRepository accounts = mock(AccountIntegrityRepository.class);
    OperationIntegrityRepository operations = mock(OperationIntegrityRepository.class);
    RecoveryQueueIntegrityRepository recovery = mock(RecoveryQueueIntegrityRepository.class);
    AdjustmentOperationIntegrityRepository outcomes =
        mock(AdjustmentOperationIntegrityRepository.class);
    AdjustmentFailureIntegrityRepository failures =
        mock(AdjustmentFailureIntegrityRepository.class);
    AdjustmentFingerprintIntegrityRepository fingerprints =
        mock(AdjustmentFingerprintIntegrityRepository.class);
    AdjustmentLedgerIntegrityRepository ledgers = mock(AdjustmentLedgerIntegrityRepository.class);
    when(accounts.findSnapshotDrift()).thenReturn(entries(1));
    when(accounts.findOrphanLedgerAccountIds()).thenReturn(entries(2));
    when(operations.findGroupDriftKeys()).thenReturn(entries(3));
    when(recovery.findQueueDriftUsers()).thenReturn(entries(4));
    when(outcomes.findOutcomeDriftKeys()).thenReturn(entries(5));
    when(failures.findFailureDriftKeys()).thenReturn(entries(6));
    when(fingerprints.findFingerprintDriftKeys()).thenReturn(entries(7));
    when(ledgers.findLedgerDriftKeys()).thenReturn(entries(8));
    Instant checkedAt = Instant.parse("2026-08-21T11:00:00Z");
    WalletIntegrityScanner scanner =
        new WalletIntegrityScanner(
            accounts,
            operations,
            recovery,
            outcomes,
            failures,
            fingerprints,
            ledgers,
            Clock.fixed(checkedAt, ZoneOffset.UTC));

    WalletIntegritySnapshot snapshot = scanner.scan();

    assertThat(snapshot.lastCheckedAt()).isEqualTo(checkedAt);
    assertThat(snapshot.accountSnapshotDrift()).isEqualTo(1);
    assertThat(snapshot.orphanAccountLedgers()).isEqualTo(2);
    assertThat(snapshot.operationGroupDrift()).isEqualTo(3);
    assertThat(snapshot.recoveryQueueDrift()).isEqualTo(4);
    assertThat(snapshot.adjustmentOutcomeDrift()).isEqualTo(5);
    assertThat(snapshot.adjustmentFailureDrift()).isEqualTo(6);
    assertThat(snapshot.adjustmentFingerprintDrift()).isEqualTo(7);
    assertThat(snapshot.adjustmentLedgerDrift()).isEqualTo(8);
  }

  private static <T> List<T> entries(int count) {
    return Collections.nCopies(count, null);
  }
}
