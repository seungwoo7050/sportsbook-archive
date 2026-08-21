package com.sportsbook.wallet.service;

import java.util.Objects;
import java.util.UUID;

record WalletTransferReceipt(
    WalletOperationResult result, UUID destinationEntryId, UUID sourceEntryId) {

  WalletTransferReceipt {
    Objects.requireNonNull(result, "result");
    Objects.requireNonNull(destinationEntryId, "destinationEntryId");
    Objects.requireNonNull(sourceEntryId, "sourceEntryId");
  }
}
