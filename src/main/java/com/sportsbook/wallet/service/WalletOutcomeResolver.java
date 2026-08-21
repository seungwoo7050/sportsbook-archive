package com.sportsbook.wallet.service;

import com.sportsbook.wallet.domain.WalletOperation;
import com.sportsbook.wallet.domain.WalletOperationStatus;
import com.sportsbook.wallet.domain.error.WalletRejectedException;
import com.sportsbook.wallet.persistence.LedgerEntryRepository;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Converts a durable operation row into its exact success or business-rejection response. */
@Component
public class WalletOutcomeResolver {

  private final LedgerEntryRepository ledger;

  public WalletOutcomeResolver(LedgerEntryRepository ledger) {
    this.ledger = ledger;
  }

  public WalletOperationResult resolve(WalletOperation operation) {
    Objects.requireNonNull(operation, "operation");
    if (operation.status() == WalletOperationStatus.REJECTED) {
      throw new WalletRejectedException(operation.failure());
    }
    if (operation.status() != WalletOperationStatus.SUCCEEDED) {
      throw new IllegalStateException("Blocked wallet operation has no final response");
    }
    return WalletOperationResult.fromExisting(
        ledger.findByOperationGroupId(operation.operationGroupId()));
  }
}
