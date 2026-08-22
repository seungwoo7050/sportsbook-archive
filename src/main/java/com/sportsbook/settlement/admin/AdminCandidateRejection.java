package com.sportsbook.settlement.admin;

import com.sportsbook.settlement.correction.ResultCandidateState;
import com.sportsbook.settlement.correction.ResultCandidateStore;
import com.sportsbook.settlement.persistence.DatabaseTimeSource;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminCandidateRejection {

  private final AdminActionRepository actions;
  private final ResultCandidateStore candidates;
  private final DatabaseTimeSource databaseTime;

  public AdminCandidateRejection(
      AdminActionRepository actions,
      ResultCandidateStore candidates,
      DatabaseTimeSource databaseTime) {
    this.actions = actions;
    this.candidates = candidates;
    this.databaseTime = databaseTime;
  }

  @Transactional
  public Decision decide(UUID idempotencyKey, UUID candidateId, String requestedReason) {
    String reason = normalize(requestedReason);
    AdminAction.Kind kind = AdminAction.Kind.CANDIDATE_REJECT;
    String fingerprint = AdminRequestFingerprint.create(kind, candidateId, reason);
    var replay =
        AdminActionReplay.requireExact(
            actions.lockAndFind(idempotencyKey), kind, candidateId, fingerprint);
    if (replay.isPresent()) {
      return new Decision(replay.orElseThrow(), true);
    }
    ResultCandidateStore.AdminCandidate candidate =
        candidates
            .lockForAdmin(candidateId)
            .orElseThrow(() -> AdminControlException.notFound("Result candidate"));
    if (candidate.state() != ResultCandidateState.PENDING) {
      throw AdminControlException.conflict("Result candidate is already decided");
    }
    if (!candidates.reject(candidateId, databaseTime.currentTimestamp(), reason)) {
      throw AdminControlException.conflict("Result candidate decision changed concurrently");
    }
    AdminAction action =
        actions.append(
            idempotencyKey,
            kind,
            candidateId,
            fingerprint,
            AdminAction.Outcome.CANDIDATE_REJECTED,
            null);
    return new Decision(action, false);
  }

  private static String normalize(String reason) {
    String normalized = reason == null ? "" : reason.strip();
    boolean control = normalized.codePoints().anyMatch(Character::isISOControl);
    if (normalized.isEmpty() || normalized.length() > 256 || control) {
      throw AdminControlException.invalid("Rejection reason must be 1 to 256 printable characters");
    }
    return normalized;
  }

  public record Decision(AdminAction action, boolean replay) {}
}
