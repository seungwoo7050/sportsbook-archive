package com.sportsbook.settlement.correction;

import com.sportsbook.settlement.client.WalletAdjustmentProof;
import com.sportsbook.settlement.client.WalletFailurePolicy;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class RevisionExecutionRunner {

  private final RevisionWalletGateway wallet;
  private final RevisionPlanRepository revisions;
  private final RevisionFinalizer finalizer;
  private final Clock clock;

  public RevisionExecutionRunner(
      RevisionWalletGateway wallet,
      RevisionPlanRepository revisions,
      RevisionFinalizer finalizer,
      Clock clock) {
    this.wallet = wallet;
    this.revisions = revisions;
    this.finalizer = finalizer;
    this.clock = clock;
  }

  public Result execute(RevisionPlan plan, RevisionLease lease, boolean recoverFirst) {
    return execute(plan, lease, recoverFirst, true);
  }

  public Result execute(
      RevisionPlan plan, RevisionLease lease, boolean recoverFirst, boolean submitWhenMissing) {
    if (!plan.requiresWalletAdjustment()) {
      return finalizer.apply(plan, lease, null, clock.instant())
          ? Result.APPLIED
          : Result.LOST_OWNERSHIP;
    }
    try {
      WalletAdjustmentProof proof =
          recoverFirst ? wallet.recoverAmbiguous(plan, submitWhenMissing) : wallet.submit(plan);
      Instant completedAt = clock.instant();
      return switch (proof.status()) {
        case APPLIED ->
            finalizer.apply(plan, lease, proof, completedAt)
                ? Result.APPLIED
                : Result.LOST_OWNERSHIP;
        case BLOCKED ->
            revisions
                .markBlocked(plan.revisionId(), lease, proof, completedAt)
                .map(state -> Result.BLOCKED)
                .orElse(Result.LOST_OWNERSHIP);
        case REJECTED ->
            revisions.markRejected(plan.revisionId(), lease, proof, completedAt)
                ? Result.REJECTED
                : Result.LOST_OWNERSHIP;
      };
    } catch (WalletFailurePolicy.TransientFailure failure) {
      return revisions
          .releaseTransient(plan.revisionId(), lease, failure)
          .map(
              state ->
                  switch (state) {
                    case BLOCKED -> Result.BLOCKED;
                    case EXHAUSTED -> Result.EXHAUSTED;
                    default -> Result.RETRY;
                  })
          .orElse(Result.LOST_OWNERSHIP);
    } catch (WalletFailurePolicy.PermanentFailure failure) {
      return revisions
          .rejectPermanent(plan.revisionId(), lease, failure, clock.instant())
          .map(state -> state == RevisionState.BLOCKED ? Result.BLOCKED : Result.REJECTED)
          .orElse(Result.LOST_OWNERSHIP);
    }
  }

  public enum Result {
    APPLIED,
    BLOCKED,
    EXHAUSTED,
    REJECTED,
    RETRY,
    LOST_OWNERSHIP
  }
}
