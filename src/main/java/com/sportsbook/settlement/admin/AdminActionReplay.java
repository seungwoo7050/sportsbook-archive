package com.sportsbook.settlement.admin;

import java.util.Optional;
import java.util.UUID;

public final class AdminActionReplay {

  private AdminActionReplay() {}

  public static Optional<AdminAction> requireExact(
      Optional<AdminAction> existing, AdminAction.Kind kind, UUID targetId, String fingerprint) {
    if (existing.isEmpty()) {
      return Optional.empty();
    }
    AdminAction action = existing.orElseThrow();
    boolean exact =
        action.kind() == kind
            && action.targetId().equals(targetId)
            && action.requestFingerprint().equals(fingerprint);
    if (!exact) {
      throw AdminControlException.conflict("Idempotency-Key is already bound to another request");
    }
    return Optional.of(action);
  }
}
