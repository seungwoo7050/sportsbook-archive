package com.sportsbook.admin.client;

import java.time.Instant;
import java.util.UUID;

public record SettlementRetryReceipt(
    UUID idempotencyKey,
    Outcome outcome,
    SettlementRevisionView.State revisionState,
    Integer attemptCount,
    Instant nextRetryAt) {

  private static final int MAX_ATTEMPTS = 12;

  public static SettlementRetryReceipt verify(UUID requestedKey, SettlementRetryReceipt receipt) {
    if (receipt == null
        || !requestedKey.equals(receipt.idempotencyKey())
        || receipt.outcome() == null
        || receipt.revisionState() == null
        || receipt.attemptCount() == null
        || receipt.attemptCount() < 0
        || receipt.attemptCount() > MAX_ATTEMPTS
        || invalidQueuedProof(receipt)) {
      throw new DownstreamContractException("matching Settlement revision retry receipt");
    }
    return receipt;
  }

  private static boolean invalidQueuedProof(SettlementRetryReceipt receipt) {
    return receipt.outcome() == Outcome.QUEUED
        && (receipt.attemptCount() != 0
            || receipt.nextRetryAt() == null
            || (receipt.revisionState() != SettlementRevisionView.State.PENDING
                && receipt.revisionState() != SettlementRevisionView.State.BLOCKED));
  }

  public enum Outcome {
    QUEUED,
    REPLAY
  }
}
