package com.sportsbook.settlement.correction;

import com.sportsbook.settlement.client.WalletAdjustmentProof;
import com.sportsbook.settlement.client.WalletFailurePolicy;
import java.util.Objects;

final class RevisionProofValidator {

  WalletAdjustmentProof requireExact(RevisionPlan plan, WalletAdjustmentProof proof) {
    RevisionTarget target = plan.target();
    if (proof == null
        || !Objects.equals(proof.revisionId(), plan.revisionId())
        || !Objects.equals(proof.betId(), target.betId())
        || proof.revisionNumber() != target.revisionNumber()
        || !Objects.equals(proof.userId(), target.userId())
        || !Objects.equals(proof.previousPayout(), target.previousPayout())
        || !Objects.equals(proof.newPayout(), plan.newPayout())
        || proof.deltaAmount() != plan.deltaAmount()
        || proof.currency() != plan.newPayout().currency()
        || proof.status() == null) {
      throw WalletFailurePolicy.malformedSuccess();
    }
    boolean valid =
        switch (proof.status()) {
          case APPLIED ->
              proof.operationGroupId() != null
                  && proof.appliedAt() != null
                  && proof.nextAttemptAt() == null
                  && ((proof.queueSequence() == null && proof.queuedAt() == null)
                      || (plan.deltaAmount() < 0
                          && proof.queueSequence() != null
                          && proof.queueSequence() > 0
                          && proof.queuedAt() != null));
          case BLOCKED ->
              plan.deltaAmount() < 0
                  && proof.queueSequence() != null
                  && proof.queueSequence() > 0
                  && proof.queuedAt() != null
                  && proof.nextAttemptAt() != null
                  && proof.operationGroupId() == null
                  && proof.appliedAt() == null;
          case REJECTED ->
              proof.queueSequence() == null
                  && proof.operationGroupId() == null
                  && proof.queuedAt() == null
                  && proof.appliedAt() == null
                  && proof.nextAttemptAt() == null;
        };
    if (!valid) {
      throw WalletFailurePolicy.malformedSuccess();
    }
    return proof;
  }
}
