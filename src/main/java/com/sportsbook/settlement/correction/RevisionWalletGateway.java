package com.sportsbook.settlement.correction;

import com.sportsbook.settlement.client.WalletAdjustmentProof;
import com.sportsbook.settlement.client.WalletClient;
import com.sportsbook.settlement.client.WalletFailurePolicy;
import org.springframework.stereotype.Component;

@Component
public class RevisionWalletGateway {

  private final WalletClient wallet;
  private final RevisionProofValidator proofs = new RevisionProofValidator();

  public RevisionWalletGateway(WalletClient wallet) {
    this.wallet = wallet;
  }

  public WalletAdjustmentProof submit(RevisionPlan plan) {
    RevisionTarget target = plan.target();
    WalletAdjustmentProof proof =
        wallet.adjust(
            plan.revisionId(),
            target.betId(),
            target.revisionNumber(),
            target.userId(),
            target.previousPayout(),
            plan.newPayout());
    return proofs.requireExact(plan, proof);
  }

  public WalletAdjustmentProof recoverAmbiguous(RevisionPlan plan, boolean submitWhenMissing) {
    try {
      return proofs.requireExact(plan, wallet.findAdjustment(plan.revisionId()));
    } catch (WalletFailurePolicy.PermanentFailure failure) {
      if (submitWhenMissing
          && failure.status() == 404
          && "WALLET_ADJUSTMENT_NOT_FOUND".equals(failure.errorCode())) {
        return submit(plan);
      }
      throw failure;
    }
  }
}
