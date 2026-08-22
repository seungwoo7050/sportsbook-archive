package com.sportsbook.settlement.correction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
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
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;

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

  @Test
  void getsBeforeRepostingOnlyWhenNoWalletProofExists() throws Exception {
    WalletClient wallet = mock(WalletClient.class);
    RevisionPlan plan = plan();
    WalletFailurePolicy.PermanentFailure missing = notFound();
    when(wallet.findAdjustment(plan.revisionId())).thenThrow(missing);
    when(wallet.adjust(
            plan.revisionId(),
            plan.target().betId(),
            1,
            plan.target().userId(),
            Money.krw(200),
            Money.krw(100)))
        .thenReturn(applied(plan, plan.target().userId()));

    new RevisionWalletGateway(wallet).recoverAmbiguous(plan);

    var ordered = inOrder(wallet);
    ordered.verify(wallet).findAdjustment(plan.revisionId());
    ordered
        .verify(wallet)
        .adjust(
            plan.revisionId(),
            plan.target().betId(),
            1,
            plan.target().userId(),
            Money.krw(200),
            Money.krw(100));
  }

  private static WalletFailurePolicy.PermanentFailure notFound() throws Exception {
    ClientHttpResponse response = mock(ClientHttpResponse.class);
    when(response.getStatusCode()).thenReturn(HttpStatus.NOT_FOUND);
    when(response.getBody())
        .thenReturn(
            new ByteArrayInputStream(
                "{\"errorCode\":\"WALLET_ADJUSTMENT_NOT_FOUND\"}"
                    .getBytes(StandardCharsets.UTF_8)));
    try {
      WalletFailurePolicy.throwFor(response);
      throw new AssertionError("Expected missing adjustment");
    } catch (WalletFailurePolicy.PermanentFailure failure) {
      return failure;
    }
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
