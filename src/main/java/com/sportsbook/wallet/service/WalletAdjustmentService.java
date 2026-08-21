package com.sportsbook.wallet.service;

import com.sportsbook.wallet.domain.WalletAdjustment;
import com.sportsbook.wallet.domain.WalletCaller;
import com.sportsbook.wallet.domain.WalletOperation;
import com.sportsbook.wallet.domain.WalletOperationKind;
import com.sportsbook.wallet.domain.WalletOperationStatus;
import com.sportsbook.wallet.domain.error.WalletRejectedException;
import com.sportsbook.wallet.persistence.WalletAdjustmentRepository;
import com.sportsbook.wallet.service.command.AdjustmentCommand;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Executes settlement corrections and returns their durable wallet proof. */
@Service
public class WalletAdjustmentService {
  private final WalletOperationExecutor operations;
  private final AdjustmentFirstWriter firstWriter;
  private final WalletAdjustmentRepository adjustments;

  public WalletAdjustmentService(
      WalletOperationExecutor operations,
      AdjustmentFirstWriter firstWriter,
      WalletAdjustmentRepository adjustments) {
    this.operations = operations;
    this.firstWriter = firstWriter;
    this.adjustments = adjustments;
  }

  public WalletAdjustment adjust(AdjustmentCommand command) {
    OperationFingerprint fingerprint =
        OperationFingerprint.adjustment(
            WalletCaller.SETTLEMENT,
            command.userId(),
            command.previousPayout(),
            command.newPayout(),
            command.revisionId(),
            command.betId(),
            command.revisionNumber());
    WalletOperation outcome =
        operations.execute(
            command.idempotencyKey(),
            WalletCaller.SETTLEMENT,
            WalletOperationKind.BET_ADJUSTMENT,
            command.userId(),
            command.absoluteDelta(),
            fingerprint,
            requestHash -> firstWriter.write(command, requestHash));
    if (outcome.status() == WalletOperationStatus.REJECTED) {
      throw new WalletRejectedException(outcome.failure());
    }
    return adjustments
        .findById(command.revisionId())
        .filter(proof -> proof.idempotencyKey().equals(command.idempotencyKey().value()))
        .orElseThrow(() -> new IllegalStateException("Adjustment outcome has no matching proof"));
  }

  public Optional<WalletAdjustment> findProof(UUID revisionId) {
    return adjustments.findById(Objects.requireNonNull(revisionId, "revisionId"));
  }
}
