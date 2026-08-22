package com.sportsbook.settlement.admin;

import com.sportsbook.settlement.observability.SettlementMetrics;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AdminRevisionCommands {

  private final AdminRevisionRetry retry;
  private final AdminRevisionQueryRepository revisions;
  private final SettlementMetrics metrics;

  public AdminRevisionCommands(
      AdminRevisionRetry retry, AdminRevisionQueryRepository revisions, SettlementMetrics metrics) {
    this.retry = retry;
    this.revisions = revisions;
    this.metrics = metrics;
  }

  public Receipt retry(UUID idempotencyKey, UUID revisionId) {
    AdminRevisionRetry.Decision decision = retry.claim(idempotencyKey, revisionId);
    AdminRevisionQueryRepository.View revision =
        revisions
            .find(revisionId)
            .orElseThrow(() -> new IllegalStateException("Queued revision is missing"));
    metrics.count("admin_retry", decision.replay() ? "replay" : "queued");
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
