package com.sportsbook.settlement.persistence;

import com.sportsbook.protocol.value.Currency;
import com.sportsbook.settlement.client.WalletAdjustmentProof;
import com.sportsbook.settlement.correction.RevisionPlan;
import java.time.Instant;
import java.util.UUID;

final class CorrectionProofs {

  private CorrectionProofs() {}

  static WalletAdjustmentProof applied(RevisionPlan plan) {
    return new WalletAdjustmentProof(
        plan.revisionId(),
        plan.target().betId(),
        plan.target().revisionNumber(),
        plan.target().userId(),
        plan.target().previousPayout(),
        plan.newPayout(),
        plan.deltaAmount(),
        Currency.KRW,
        WalletAdjustmentProof.Status.APPLIED,
        null,
        UUID.randomUUID(),
        null,
        Instant.parse("2026-08-22T00:00:02Z"),
        null);
  }
}
