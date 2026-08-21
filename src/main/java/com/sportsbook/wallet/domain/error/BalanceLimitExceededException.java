package com.sportsbook.wallet.domain.error;

import java.util.UUID;

/** Raised when available plus locked funds cannot be represented as a signed long. */
public final class BalanceLimitExceededException extends RuntimeException {

  private final UUID userId;
  private final long availableAmount;
  private final long lockedAmount;

  public BalanceLimitExceededException(UUID userId, long availableAmount, long lockedAmount) {
    super(
        "Account "
            + userId
            + " balance exceeds Long.MAX_VALUE: available="
            + availableAmount
            + ", locked="
            + lockedAmount);
    this.userId = userId;
    this.availableAmount = availableAmount;
    this.lockedAmount = lockedAmount;
  }

  public UUID userId() {
    return userId;
  }

  public long availableAmount() {
    return availableAmount;
  }

  public long lockedAmount() {
    return lockedAmount;
  }
}
