package com.sportsbook.settlement.admin;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record AdminAction(
    UUID idempotencyKey,
    Kind kind,
    UUID targetId,
    String requestFingerprint,
    Outcome outcome,
    UUID executionToken,
    Instant createdAt,
    Instant completedAt) {

  public AdminAction {
    Objects.requireNonNull(idempotencyKey, "idempotencyKey");
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(targetId, "targetId");
    Objects.requireNonNull(requestFingerprint, "requestFingerprint");
    Objects.requireNonNull(outcome, "outcome");
    Objects.requireNonNull(createdAt, "createdAt");
    Objects.requireNonNull(completedAt, "completedAt");
    if ((kind == Kind.REVISION_RETRY) != (executionToken != null)) {
      throw new IllegalArgumentException("Only revision retries carry an execution token");
    }
  }

  public enum Kind {
    CANDIDATE_APPROVE,
    CANDIDATE_REJECT,
    REVISION_RETRY
  }

  public enum Outcome {
    CANDIDATE_APPROVED,
    CANDIDATE_REJECTED,
    REVISION_RETRY_QUEUED
  }
}
