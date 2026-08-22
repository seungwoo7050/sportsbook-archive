package com.sportsbook.settlement.admin;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AdminRevisionCommands {

  private final AdminRevisionRetry retry;
  private final AdminRevisionQueryRepository revisions;

  public AdminRevisionCommands(AdminRevisionRetry retry, AdminRevisionQueryRepository revisions) {
    this.retry = retry;
    this.revisions = revisions;
  }

  public Receipt retry(UUID idempotencyKey, UUID revisionId) {
    AdminRevisionRetry.Decision decision = retry.claim(idempotencyKey, revisionId);
    AdminRevisionQueryRepository.View revision =
        revisions
            .find(revisionId)
            .orElseThrow(() -> new IllegalStateException("Queued revision is missing"));
    return new Receipt(
        decision.action().idempotencyKey(),
        decision.replay() ? "REPLAY" : "QUEUED",
        revision.state(),
        revision.attemptCount(),
        revision.nextRetryAt());
  }

  public record Receipt(
      UUID idempotencyKey,
      String outcome,
      String revisionState,
      int attemptCount,
      Instant nextRetryAt) {}
}
