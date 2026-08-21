package com.sportsbook.wallet.domain.error;

import java.util.Objects;
import java.util.UUID;

/** Signals that recovery debt has frozen new outbound spending for an account. */
public final class AccountRecoveryBlockedException extends RuntimeException {
  private final UUID userId;

  public AccountRecoveryBlockedException(UUID userId) {
    super("Account is frozen for recovery: " + Objects.requireNonNull(userId, "userId"));
    this.userId = userId;
  }

  public UUID userId() {
    return userId;
  }
}
