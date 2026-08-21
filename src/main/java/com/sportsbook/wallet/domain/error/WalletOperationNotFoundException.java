package com.sportsbook.wallet.domain.error;

import java.util.Objects;
import java.util.UUID;

/** Raised when a bet has no durable wallet debit proof. */
public final class WalletOperationNotFoundException extends RuntimeException {
  private final UUID betId;

  public WalletOperationNotFoundException(UUID betId) {
    super("No wallet debit exists for bet " + Objects.requireNonNull(betId, "betId"));
    this.betId = betId;
  }

  public UUID betId() {
    return betId;
  }
}
