package com.sportsbook.admin.client;

import java.time.Instant;
import java.util.UUID;

class SettlementRevisionWalletProofTest {

  private static final UUID REVISION = UUID.fromString("018f0000-0000-7000-8000-000000000182");
  private static final UUID OPERATION = UUID.fromString("018f0000-0000-7000-8000-000000000183");
  private static final Instant CREATED = Instant.parse("2026-08-22T01:00:00Z");
  private static final Instant QUEUED = CREATED.plusSeconds(1);
  private static final Instant DUE = CREATED.plusSeconds(2);
  private static final Instant UPDATED = CREATED.plusSeconds(3);

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
