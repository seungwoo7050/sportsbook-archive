package com.sportsbook.settlement.admin;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminRevisionRetry {

  private final AdminActionRepository actions;
  private final AdminRevisionRetryRepository retries;
  private final AdminRevisionQueryRepository revisions;

  public AdminRevisionRetry(
      AdminActionRepository actions,
      AdminRevisionRetryRepository retries,
      AdminRevisionQueryRepository revisions) {
    this.actions = actions;
    this.retries = retries;
    this.revisions = revisions;
  }

  @Transactional
  public Decision claim(UUID idempotencyKey, UUID revisionId) {
    AdminAction.Kind kind = AdminAction.Kind.REVISION_RETRY;
    String fingerprint = AdminRequestFingerprint.create(kind, revisionId, "");
    var replay =
        AdminActionReplay.requireExact(
            actions.lockAndFind(idempotencyKey), kind, revisionId, fingerprint);
    if (replay.isPresent()) {
      return new Decision(replay.orElseThrow(), true);
    }
    UUID token = UUID.randomUUID();
    if (retries.queue(revisionId).isEmpty()) {
      if (revisions.find(revisionId).isEmpty()) {
        throw AdminControlException.notFound("Settlement revision");
      }
      throw AdminControlException.conflict("Settlement revision is not paused for operator retry");
    }
    AdminAction action =
        actions.append(
            idempotencyKey,
            kind,
            revisionId,
            fingerprint,
            AdminAction.Outcome.REVISION_RETRY_QUEUED,
            token);
    return new Decision(action, false);
  }

  public record Decision(AdminAction action, boolean replay) {}
}
