package com.sportsbook.settlement.correction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sportsbook.protocol.domain.BetSlipType;
import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.Odds;
import com.sportsbook.settlement.client.WalletAdjustmentProof;
import com.sportsbook.settlement.client.WalletClient;
import com.sportsbook.settlement.client.WalletFailurePolicy;
import com.sportsbook.settlement.resolver.ResolvedSelection;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RevisionWalletGatewayTest {

  @Test
  void submitsAndAcceptsOnlyTheExactAppliedProof() {
    WalletClient wallet = mock(WalletClient.class);
    RevisionPlan plan = plan();
    WalletAdjustmentProof proof = applied(plan, plan.target().userId());
    when(wallet.adjust(
            plan.revisionId(),
            plan.target().betId(),
            1,
            plan.target().userId(),
            Money.krw(200),
            Money.krw(100)))
        .thenReturn(proof);

    assertThat(new RevisionWalletGateway(wallet).submit(plan)).isSameAs(proof);
  }

  @Test
  void rejectsAProofForAnotherPersistedSnapshot() {
    WalletClient wallet = mock(WalletClient.class);
    RevisionPlan plan = plan();
    when(wallet.adjust(
            plan.revisionId(),
            plan.target().betId(),
            1,
            plan.target().userId(),
            Money.krw(200),
            Money.krw(100)))
        .thenReturn(applied(plan, UUID.randomUUID()));

    assertThatThrownBy(() -> new RevisionWalletGateway(wallet).submit(plan))
        .isInstanceOf(WalletFailurePolicy.TransientFailure.class)
        .hasMessageContaining("WALLET_MALFORMED_RESPONSE");
  }

  private static WalletAdjustmentProof applied(RevisionPlan plan, UUID userId) {
    return new WalletAdjustmentProof(
        plan.revisionId(),
        plan.target().betId(),
        1,
        userId,
        Money.krw(200),
        Money.krw(100),
        -100,
        Currency.KRW,
        WalletAdjustmentProof.Status.APPLIED,
        null,
        UUID.randomUUID(),
        null,
        Instant.EPOCH,
        null);
  }

  private static RevisionPlan plan() {
    RevisionTarget target =
        new RevisionTarget(
            UUID.randomUUID(),
            1,
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            SettlementResult.WON,
            Money.krw(200),
            new BetSlipType.Single(),
            Money.krw(100),
            List.of(
                new ResolvedSelection(
                    UUID.randomUUID(), Odds.ofDecimal("2.0000"), SettlementResult.PUSH)),
            Instant.EPOCH);
    return new RevisionPlan(
        UUID.randomUUID(), target, SettlementResult.PUSH, Money.krw(100), Instant.EPOCH);
  }
}
