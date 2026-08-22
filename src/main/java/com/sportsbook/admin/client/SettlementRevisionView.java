package com.sportsbook.admin.client;

import java.time.Instant;
import java.util.UUID;

public record SettlementRevisionView(
    UUID revisionId,
    UUID betId,
    Long revisionNumber,
    UUID eventId,
    UUID sourceCandidateId,
    State state,
    Integer attemptCount,
    Instant nextRetryAt,
    String lastErrorCode,
    Instant leaseUntil,
    WalletStatus walletStatus,
    Long walletQueueSequence,
    UUID walletOperationGroupId,
    Instant walletQueuedAt,
    Instant walletAppliedAt,
    Instant walletNextAttemptAt,
    Instant createdAt,
    Instant updatedAt,
    Instant appliedAt) {

  public enum State {
    PENDING,
    BLOCKED,
    EXHAUSTED,
    APPLIED,
    REJECTED
  }

  public enum WalletStatus {
    BLOCKED,
    APPLIED,
    REJECTED
  }
}
