package com.sportsbook.settlement.correction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sportsbook.protocol.domain.BetSlipType;
import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.Odds;
import com.sportsbook.settlement.client.WalletAdjustmentProof;
import com.sportsbook.settlement.client.WalletFailurePolicy;
import com.sportsbook.settlement.resolver.ResolvedSelection;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;

class RevisionExecutionRunnerTest {

  private final RevisionWalletGateway wallet = mock(RevisionWalletGateway.class);
  private final RevisionPlanRepository revisions = mock(RevisionPlanRepository.class);
  private final RevisionFinalizer finalizer = mock(RevisionFinalizer.class);
  private final Instant now = Instant.parse("2026-08-22T00:00:00Z");
  private final RevisionExecutionRunner runner =
      new RevisionExecutionRunner(wallet, revisions, finalizer, Clock.fixed(now, ZoneOffset.UTC));

  @Test
  void finalizesZeroDeltaPlansWithoutCallingWallet() {
    RevisionPlan plan = plan(200);
    RevisionLease lease = new RevisionLease(UUID.randomUUID(), Instant.MAX);
    when(finalizer.apply(plan, lease, null, now)).thenReturn(true);

    assertThat(runner.execute(plan, lease, false))
        .isEqualTo(RevisionExecutionRunner.Result.APPLIED);
    verify(finalizer).apply(plan, lease, null, now);
    verifyNoInteractions(wallet);
  }

  @Test
  void reportsOwnerLossWhenDatabaseTimedReleaseDoesNotMatch() {
    RevisionPlan plan = plan(100);
    RevisionLease lease = new RevisionLease(UUID.randomUUID(), Instant.MAX);
    WalletFailurePolicy.TransientFailure failure = WalletFailurePolicy.malformedSuccess();
    when(wallet.submit(plan)).thenThrow(failure);
    when(revisions.releaseTransient(plan.revisionId(), lease, failure))
        .thenReturn(java.util.Optional.empty());

    assertThat(runner.execute(plan, lease, false))
        .isEqualTo(RevisionExecutionRunner.Result.LOST_OWNERSHIP);
    var ordered = inOrder(wallet, revisions);
    ordered.verify(wallet).submit(plan);
    ordered.verify(revisions).releaseTransient(plan.revisionId(), lease, failure);
  }

  @Test
  void reportsRetryExhaustionWithoutForgingARejection() {
    RevisionPlan plan = plan(100);
    RevisionLease lease = new RevisionLease(UUID.randomUUID(), Instant.MAX);
    WalletFailurePolicy.TransientFailure failure = WalletFailurePolicy.malformedSuccess();
    when(wallet.submit(plan)).thenThrow(failure);
    when(revisions.releaseTransient(plan.revisionId(), lease, failure))
        .thenReturn(java.util.Optional.of(RevisionState.EXHAUSTED));

    assertThat(runner.execute(plan, lease, false))
        .isEqualTo(RevisionExecutionRunner.Result.EXHAUSTED);
  }

  @Test
  void pausesAClaimWhenItsBlockedProofIsMissing() throws Exception {
    RevisionPlan plan = plan(100);
    RevisionLease lease = new RevisionLease(UUID.randomUUID(), Instant.MAX);
    WalletFailurePolicy.PermanentFailure missing = missingAdjustment();
    when(wallet.recoverAmbiguous(plan, false)).thenThrow(missing);
    when(revisions.rejectPermanent(plan.revisionId(), lease, missing, now))
        .thenReturn(java.util.Optional.of(RevisionState.BLOCKED));

    assertThat(runner.execute(plan, lease, true, false))
        .isEqualTo(RevisionExecutionRunner.Result.BLOCKED);
    verify(wallet).recoverAmbiguous(plan, false);
    verify(revisions).rejectPermanent(plan.revisionId(), lease, missing, now);
  }

  @Test
  void routesAuthoritativeRecoveredProofsThroughOwnerFencedTransitions() {
    RevisionPlan plan = plan(100);
    RevisionLease lease = new RevisionLease(UUID.randomUUID(), Instant.MAX);
    WalletAdjustmentProof applied = proof(plan, WalletAdjustmentProof.Status.APPLIED);
    WalletAdjustmentProof blocked = proof(plan, WalletAdjustmentProof.Status.BLOCKED);
    WalletAdjustmentProof rejected = proof(plan, WalletAdjustmentProof.Status.REJECTED);
    when(wallet.recoverAmbiguous(plan, true)).thenReturn(applied, blocked, rejected);
    when(finalizer.apply(plan, lease, applied, now)).thenReturn(true);
    when(revisions.markBlocked(plan.revisionId(), lease, blocked, now))
        .thenReturn(java.util.Optional.of(RevisionState.BLOCKED));
    when(revisions.markRejected(plan.revisionId(), lease, rejected, now)).thenReturn(true);

    assertThat(runner.execute(plan, lease, true, true))
        .isEqualTo(RevisionExecutionRunner.Result.APPLIED);
    assertThat(runner.execute(plan, lease, true, true))
        .isEqualTo(RevisionExecutionRunner.Result.BLOCKED);
    assertThat(runner.execute(plan, lease, true, true))
        .isEqualTo(RevisionExecutionRunner.Result.REJECTED);
    verify(finalizer).apply(plan, lease, applied, now);
    verify(revisions).markBlocked(plan.revisionId(), lease, blocked, now);
    verify(revisions).markRejected(plan.revisionId(), lease, rejected, now);
  }

  private static WalletFailurePolicy.PermanentFailure missingAdjustment() throws Exception {
    ClientHttpResponse response = mock(ClientHttpResponse.class);
    when(response.getStatusCode()).thenReturn(HttpStatus.NOT_FOUND);
    when(response.getBody())
        .thenReturn(
            new ByteArrayInputStream(
                "{\"errorCode\":\"WALLET_ADJUSTMENT_NOT_FOUND\"}"
                    .getBytes(StandardCharsets.UTF_8)));
    try {
      WalletFailurePolicy.throwFor(response);
      throw new AssertionError("Expected missing Wallet adjustment");
    } catch (WalletFailurePolicy.PermanentFailure failure) {
      return failure;
    }
  }

  private static WalletAdjustmentProof proof(
      RevisionPlan plan, WalletAdjustmentProof.Status status) {
    return new WalletAdjustmentProof(
        plan.revisionId(),
        plan.target().betId(),
        plan.target().revisionNumber(),
        plan.target().userId(),
        plan.target().previousPayout(),
        plan.newPayout(),
        plan.deltaAmount(),
        Currency.KRW,
        status,
        status == WalletAdjustmentProof.Status.BLOCKED ? 1L : null,
        status == WalletAdjustmentProof.Status.APPLIED ? UUID.randomUUID() : null,
        status == WalletAdjustmentProof.Status.BLOCKED ? Instant.EPOCH : null,
        status == WalletAdjustmentProof.Status.APPLIED ? Instant.EPOCH : null,
        status == WalletAdjustmentProof.Status.BLOCKED ? Instant.EPOCH.plusSeconds(1) : null);
  }

  private static RevisionPlan plan(long newPayout) {
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
        UUID.randomUUID(), target, SettlementResult.PUSH, Money.krw(newPayout), Instant.EPOCH);
  }
}
