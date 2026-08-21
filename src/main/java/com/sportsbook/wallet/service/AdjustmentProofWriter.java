package com.sportsbook.wallet.service;

import com.sportsbook.wallet.domain.Account;
import com.sportsbook.wallet.domain.WalletAdjustment;
import com.sportsbook.wallet.domain.WalletCaller;
import com.sportsbook.wallet.domain.WalletOperation;
import com.sportsbook.wallet.domain.WalletOperationKind;
import com.sportsbook.wallet.persistence.WalletAdjustmentRepository;
import com.sportsbook.wallet.service.command.AdjustmentCommand;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Persists adjustment ledger effects, proof, and operation outcome in the caller transaction. */
@Component
public class AdjustmentProofWriter {
  private final WalletTransferWriter transfers;
  private final WalletAdjustmentRepository adjustments;

  public AdjustmentProofWriter(
      WalletTransferWriter transfers, WalletAdjustmentRepository adjustments) {
    this.transfers = transfers;
    this.adjustments = adjustments;
  }

  public WalletOperation applyIncrease(
      AdjustmentCommand command,
      String fingerprint,
      Account account,
      Optional<WalletAdjustment> blockedHead,
      Instant now) {
    WalletTransferPlan plan = AdjustmentTransfers.increase(account, command, now);
    WalletOperationResult result =
        transfers.write(
            plan.destination(),
            plan.source(),
            command.absoluteDelta(),
            plan.reason(),
            command.idempotencyKey(),
            command.userId(),
            now);
    adjustments.save(WalletAdjustment.applied(command, result.operationGroupId(), result.at()));
    blockedHead.ifPresent(head -> head.wake(now));
    return WalletOperation.succeeded(
        command.idempotencyKey(),
        WalletCaller.SETTLEMENT,
        WalletOperationKind.BET_ADJUSTMENT,
        command.userId(),
        command.absoluteDelta(),
        fingerprint,
        result.operationGroupId(),
        now);
  }
}
