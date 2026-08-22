package com.sportsbook.settlement.correction;

import com.sportsbook.settlement.client.WalletAdjustmentProof;
import com.sportsbook.settlement.client.WalletClient;
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
}
