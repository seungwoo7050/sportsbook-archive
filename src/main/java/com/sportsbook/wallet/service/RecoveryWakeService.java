package com.sportsbook.wallet.service;

import com.sportsbook.wallet.domain.Account;
import com.sportsbook.wallet.domain.WalletAdjustment;
import com.sportsbook.wallet.persistence.DatabaseClock;
import com.sportsbook.wallet.persistence.WalletAdjustmentRepository;
import org.springframework.stereotype.Component;

/** Moves only a frozen account's FIFO head forward after inflow mutation. */
@Component
public class RecoveryWakeService {
  private final WalletAdjustmentRepository adjustments;
  private final DatabaseClock databaseClock;

  public RecoveryWakeService(WalletAdjustmentRepository adjustments, DatabaseClock databaseClock) {
    this.adjustments = adjustments;
    this.databaseClock = databaseClock;
  }

  public void wake(Account lockedAccount) {
    if (!lockedAccount.isOutboundFrozen()) {
      return;
    }
    WalletAdjustment head =
        adjustments
            .findOldestBlockedForUpdate(lockedAccount.userId())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Recovery debt has no FIFO head for " + lockedAccount.userId()));
    head.wake(databaseClock.now());
  }
}
