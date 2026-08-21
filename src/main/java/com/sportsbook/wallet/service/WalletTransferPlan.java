package com.sportsbook.wallet.service;

import com.sportsbook.wallet.domain.LedgerEntry;
import com.sportsbook.wallet.domain.LedgerReason;
import java.util.Objects;

/** Balance mutation's matching journal topology. */
record WalletTransferPlan(
    LedgerEntry.TransferLeg destination, LedgerEntry.TransferLeg source, LedgerReason reason) {

  WalletTransferPlan {
    Objects.requireNonNull(destination, "destination");
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(reason, "reason");
  }
}
