package com.sportsbook.settlement.correction;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.protocol.domain.BetSlipType;
import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.Odds;
import com.sportsbook.settlement.client.WalletAdjustmentProof;
import com.sportsbook.settlement.client.WalletFailurePolicy;
import com.sportsbook.settlement.resolver.ResolvedSelection;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RevisionProofQueueTest {

  @Test
  void permitsOnlyPositiveQueueIdentityFromAPreviouslyBlockedNegativeDelta() {
    RevisionPlan negative = plan(200, 100);
    RevisionPlan positive = plan(100, 200);

    List.of(proof(negative, 0L), proof(negative, -1L))
        .forEach(
            proof ->
                assertThatThrownBy(() -> new RevisionProofValidator().requireExact(negative, proof))
                    .isInstanceOf(WalletFailurePolicy.TransientFailure.class));
    assertThatThrownBy(
            () -> new RevisionProofValidator().requireExact(positive, proof(positive, 1L)))
        .isInstanceOf(WalletFailurePolicy.TransientFailure.class);
    assertThatNoException()
        .isThrownBy(() -> new RevisionProofValidator().requireExact(negative, proof(negative, 1L)));
  }

  private static WalletAdjustmentProof proof(RevisionPlan plan, long sequence) {
    return new WalletAdjustmentProof(
        plan.revisionId(),
        plan.target().betId(),
        1,
        plan.target().userId(),
        plan.target().previousPayout(),
        plan.newPayout(),
        plan.deltaAmount(),
        Currency.KRW,
        WalletAdjustmentProof.Status.APPLIED,
        sequence,
        UUID.randomUUID(),
        Instant.EPOCH,
        Instant.EPOCH.plusSeconds(1),
        null);
  }

  private static RevisionPlan plan(long previous, long replacement) {
    RevisionTarget target =
        new RevisionTarget(
            UUID.randomUUID(),
            1,
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            SettlementResult.WON,
            Money.krw(previous),
            new BetSlipType.Single(),
            Money.krw(100),
            List.of(
                new ResolvedSelection(
                    UUID.randomUUID(), Odds.ofDecimal("2.0000"), SettlementResult.PUSH)),
            Instant.EPOCH);
    return new RevisionPlan(
        UUID.randomUUID(), target, SettlementResult.PUSH, Money.krw(replacement), Instant.EPOCH);
  }
}
