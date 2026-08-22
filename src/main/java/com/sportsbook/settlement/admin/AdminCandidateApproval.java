package com.sportsbook.settlement.admin;

import com.sportsbook.settlement.correction.ResultCandidateState;
import com.sportsbook.settlement.correction.ResultCandidateStore;
import com.sportsbook.settlement.persistence.DatabaseTimeSource;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminCandidateApproval {

  private final AdminActionRepository actions;
  private final ResultCandidateStore candidates;
  private final DatabaseTimeSource databaseTime;

  public AdminCandidateApproval(
      AdminActionRepository actions,
      ResultCandidateStore candidates,
      DatabaseTimeSource databaseTime) {
    this.actions = actions;
    this.candidates = candidates;
    this.databaseTime = databaseTime;
  }

  @Transactional
  public Decision decide(UUID idempotencyKey, UUID candidateId) {
    AdminAction.Kind kind = AdminAction.Kind.CANDIDATE_APPROVE;
    String fingerprint = AdminRequestFingerprint.create(kind, candidateId, "");
    var replay =
        AdminActionReplay.requireExact(
            actions.lockAndFind(idempotencyKey), kind, candidateId, fingerprint);
    ResultCandidateStore.AdminCandidate candidate =
        candidates
            .lockForAdmin(candidateId)
            .orElseThrow(() -> AdminControlException.notFound("Result candidate"));
    if (replay.isPresent()) {
      return new Decision(replay.orElseThrow(), candidate.eventId(), true);
    }
    Instant decidedAt = databaseTime.currentTimestamp();
    requireEligible(candidate, decidedAt);
    boolean approved =
        candidate.acceptedCandidateId() == null
            ? candidates.approveFirst(candidateId, decidedAt)
            : candidates.approve(candidateId, decidedAt);
    if (!approved) {
      throw AdminControlException.conflict("Result candidate decision changed concurrently");
    }
    AdminAction action =
        actions.append(
            idempotencyKey,
            kind,
            candidateId,
            fingerprint,
            AdminAction.Outcome.CANDIDATE_APPROVED,
            null);
    return new Decision(action, candidate.eventId(), false);
  }

  private static void requireEligible(
      ResultCandidateStore.AdminCandidate candidate, Instant databaseNow) {
    if (candidate.state() != ResultCandidateState.PENDING) {
      throw AdminControlException.conflict("Result candidate is already decided");
    }
    if (candidate.settledAt().isAfter(databaseNow)) {
      throw AdminControlException.conflict("Result candidate is not due");
    }
    if (candidate.acceptedCandidateId() != null
        && !Objects.equals(candidate.replacesCandidateId(), candidate.acceptedCandidateId())) {
      throw AdminControlException.conflict("Result candidate predecessor is stale");
    }
  }

  public record Decision(AdminAction action, UUID eventId, boolean replay) {}
}
