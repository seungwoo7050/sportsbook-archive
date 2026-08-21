package com.sportsbook.wallet.outbox;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record OutboxLease(UUID eventId, String owner, long version, Instant leaseUntil) {

  public OutboxLease {
    eventId = Objects.requireNonNull(eventId, "eventId");
    if (owner == null || owner.isBlank()) {
      throw new IllegalArgumentException("owner must not be blank");
    }
    if (version < 1) {
      throw new IllegalArgumentException("version must be positive");
    }
    leaseUntil = Objects.requireNonNull(leaseUntil, "leaseUntil");
  }

  public boolean isOwnedBy(String candidateOwner, long candidateVersion) {
    return owner.equals(candidateOwner) && version == candidateVersion;
  }
}
