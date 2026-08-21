package com.sportsbook.wallet.service;

import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.wallet.domain.WalletAdjustment;
import com.sportsbook.wallet.persistence.WalletAdjustmentRepository;
import com.sportsbook.wallet.persistence.WalletOperationRepository;
import java.time.Instant;
import org.springframework.stereotype.Component;

/** Applies or durably backs off one already locked FIFO recovery claim. */
@Component
class RecoveryHeadProcessor {
  enum Result {
    APPLIED,
    DEFERRED_FUNDS
  }

  private final RecoveryRetryPolicy retries;
  private final WalletTransferWriter transfers;
  private final WalletAdjustmentRepository adjustments;
  private final WalletOperationRepository operations;

  RecoveryHeadProcessor(
      RecoveryRetryPolicy retries,
      WalletTransferWriter transfers,
      WalletAdjustmentRepository adjustments,
      WalletOperationRepository operations) {
    this.retries = retries;
    this.transfers = transfers;
    this.adjustments = adjustments;
    this.operations = operations;
  }

  Result process(RecoveryClaim claim, Instant now) {
    if (claim.account().available().amount() < claim.amount().amount()) {
      claim.proof().deferUntil(now, retries.retryAt(now, claim.proof().retryCount()));
      return Result.DEFERRED_FUNDS;
    }
    WalletTransferPlan plan = AdjustmentTransfers.recover(claim.account(), claim.proof(), now);
    WalletTransferReceipt receipt =
        transfers.writeReceipt(
            plan.destination(),
            plan.source(),
            claim.amount(),
            plan.reason(),
            IdempotencyKey.of(claim.proof().idempotencyKey()),
            claim.proof().userId(),
            now);
    claim.proof().completeRecovery(receipt.result().operationGroupId(), now);
    claim.operation().completeBlocked(receipt.result().operationGroupId(), now);
    wakeOrVerifyEmpty(claim, now);
    return Result.APPLIED;
  }

  private void wakeOrVerifyEmpty(RecoveryClaim claim, Instant now) {
    adjustments.flush();
    operations.flush();
    WalletAdjustment next =
        adjustments.findOldestBlockedForUpdate(claim.account().userId()).orElse(null);
    if (claim.account().isOutboundFrozen() && next == null) {
      throw new IllegalStateException("Recovery debt has no remaining FIFO head");
    }
    if (!claim.account().isOutboundFrozen() && next != null) {
      throw new IllegalStateException("Recovery FIFO head has no remaining debt");
    }
    if (next != null) {
      next.wake(now);
    }
  }
}
