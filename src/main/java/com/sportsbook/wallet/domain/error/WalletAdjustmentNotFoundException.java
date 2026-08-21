package com.sportsbook.wallet.domain.error;

import java.util.Objects;
import java.util.UUID;

/** Raised when a settlement revision has no durable wallet adjustment proof. */
public final class WalletAdjustmentNotFoundException extends RuntimeException {
  private final UUID revisionId;

  public WalletAdjustmentNotFoundException(UUID revisionId) {
    super(
        "No wallet adjustment exists for revision "
            + Objects.requireNonNull(revisionId, "revisionId"));
    this.revisionId = revisionId;
  }

  public UUID revisionId() {
    return revisionId;
  }
}
