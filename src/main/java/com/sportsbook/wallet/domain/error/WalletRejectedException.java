package com.sportsbook.wallet.domain.error;

import com.sportsbook.wallet.domain.WalletFailureSnapshot;
import java.util.Objects;

/** Replays the immutable business failure committed for an idempotent wallet request. */
public final class WalletRejectedException extends RuntimeException {

  private final WalletFailureSnapshot failure;

  public WalletRejectedException(WalletFailureSnapshot failure) {
    super(Objects.requireNonNull(failure, "failure").detail());
    this.failure = failure;
  }

  public WalletFailureSnapshot failure() {
    return failure;
  }
}
