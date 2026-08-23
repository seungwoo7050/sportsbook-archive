package com.sportsbook.admin.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SettlementRevisionWalletProofTest {

  private static final UUID REVISION = UUID.fromString("018f0000-0000-7000-8000-000000000182");
  private static final UUID OPERATION = UUID.fromString("018f0000-0000-7000-8000-000000000183");
  private static final Instant CREATED = Instant.parse("2026-08-22T01:00:00Z");
  private static final Instant QUEUED = CREATED.plusSeconds(1);
  private static final Instant DUE = CREATED.plusSeconds(2);
  private static final Instant UPDATED = CREATED.plusSeconds(3);

  @Test
  void acceptsBlockedAppliedRejectedAndExhaustedWalletProofs() {
    List<SettlementRevisionView> valid =
        List.of(
            view(
                SettlementRevisionView.State.BLOCKED,
                SettlementRevisionView.WalletStatus.BLOCKED,
                17L,
                null,
                QUEUED,
                null,
                DUE),
            view(
                SettlementRevisionView.State.APPLIED,
                SettlementRevisionView.WalletStatus.APPLIED,
                17L,
                OPERATION,
                QUEUED,
                UPDATED,
                null),
            view(
                SettlementRevisionView.State.REJECTED,
                SettlementRevisionView.WalletStatus.REJECTED,
                null,
                null,
                null,
                null,
                null),
            view(SettlementRevisionView.State.EXHAUSTED, null, null, null, null, null, null));

    valid.forEach(
        evidence ->
            assertThat(SettlementRevisionProof.verify(REVISION, evidence)).isSameAs(evidence));
  }

  private static SettlementRevisionView view(
      SettlementRevisionView.State state,
      SettlementRevisionView.WalletStatus walletStatus,
      Long queueSequence,
      UUID operationGroup,
      Instant queuedAt,
      Instant walletAppliedAt,
      Instant walletNextAttemptAt) {
    boolean active =
        state == SettlementRevisionView.State.PENDING
            || state == SettlementRevisionView.State.BLOCKED;
    String error =
        state == SettlementRevisionView.State.EXHAUSTED
                || state == SettlementRevisionView.State.REJECTED
                || walletStatus == SettlementRevisionView.WalletStatus.BLOCKED
            ? "WALLET_ERROR"
            : null;
    return new SettlementRevisionView(
        REVISION,
        UUID.fromString("018f0000-0000-7000-8000-000000000184"),
        2L,
        UUID.fromString("018f0000-0000-7000-8000-000000000185"),
        UUID.fromString("018f0000-0000-7000-8000-000000000186"),
        state,
        3,
        active ? DUE : null,
        error,
        null,
        walletStatus,
        queueSequence,
        operationGroup,
        queuedAt,
        walletAppliedAt,
        walletNextAttemptAt,
        CREATED,
        UPDATED,
        state == SettlementRevisionView.State.APPLIED ? UPDATED : null);
  }
}
