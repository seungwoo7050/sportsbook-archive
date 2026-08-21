package com.sportsbook.wallet.service;

import com.sportsbook.protocol.value.Money;
import com.sportsbook.wallet.domain.Account;
import com.sportsbook.wallet.domain.AdjustmentStatus;
import com.sportsbook.wallet.domain.BalanceBucket;
import com.sportsbook.wallet.domain.LedgerEntry;
import com.sportsbook.wallet.domain.LedgerReason;
import com.sportsbook.wallet.domain.SystemAccountIds;
import com.sportsbook.wallet.domain.WalletAdjustment;
import com.sportsbook.wallet.service.command.AdjustmentCommand;
import java.time.Instant;

/** Mutates an account and describes the matching adjustment ledger topology. */
final class AdjustmentTransfers {
  static WalletTransferPlan increase(Account account, AdjustmentCommand command, Instant now) {
    if (command.deltaAmount() <= 0L) {
      throw new IllegalArgumentException("Increase transfer requires a positive delta");
    }
    account.increaseAvailable(command.absoluteDelta(), now);
    return new WalletTransferPlan(
        new LedgerEntry.TransferLeg(command.userId(), BalanceBucket.AVAILABLE),
        new LedgerEntry.TransferLeg(SystemAccountIds.HOUSE, BalanceBucket.AVAILABLE),
        LedgerReason.BET_ADJUSTMENT);
  }

  static WalletTransferPlan decrease(Account account, AdjustmentCommand command, Instant now) {
    if (command.deltaAmount() >= 0L) {
      throw new IllegalArgumentException("Decrease transfer requires a negative delta");
    }
    account.decreaseAvailable(command.absoluteDelta(), now);
    return new WalletTransferPlan(
        new LedgerEntry.TransferLeg(SystemAccountIds.HOUSE, BalanceBucket.AVAILABLE),
        new LedgerEntry.TransferLeg(command.userId(), BalanceBucket.AVAILABLE),
        LedgerReason.BET_ADJUSTMENT);
  }

  static WalletTransferPlan recover(Account account, WalletAdjustment proof, Instant now) {
    if (proof.status() != AdjustmentStatus.BLOCKED || proof.deltaAmount() >= 0L) {
      throw new IllegalArgumentException("Recovery requires a blocked negative adjustment");
    }
    if (!account.userId().equals(proof.userId())) {
      throw new IllegalArgumentException("Recovery account does not own the adjustment");
    }
    account.recoverAvailable(new Money(-proof.deltaAmount(), proof.currency()), now);
    return new WalletTransferPlan(
        new LedgerEntry.TransferLeg(SystemAccountIds.HOUSE, BalanceBucket.AVAILABLE),
        new LedgerEntry.TransferLeg(proof.userId(), BalanceBucket.AVAILABLE),
        LedgerReason.BET_ADJUSTMENT);
  }

  private AdjustmentTransfers() {}
}
