package com.sportsbook.wallet.domain.error;

import java.util.UUID;

/** Raised when a wallet operation addresses a user account that has not been opened. */
public final class AccountNotFoundException extends RuntimeException {

  private final UUID userId;

  public AccountNotFoundException(UUID userId) {
    super("Wallet account does not exist: " + userId);
    this.userId = userId;
  }

  public UUID userId() {
    return userId;
  }
}
