package com.sportsbook.admin.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SettlementRevisionProofTest {

  private static final UUID REVISION = UUID.fromString("018f0000-0000-7000-8000-000000000151");
  private static final Instant CREATED = Instant.parse("2026-08-22T01:00:00Z");

  @Test
  void acceptsPendingAndAppliedLifecycleProofs() {
    SettlementRevisionView pending =
        view(REVISION, 1L, SettlementRevisionView.State.PENDING, 0, CREATED, CREATED, null, null);
    SettlementRevisionView applied =
        view(
            REVISION,
            2L,
            SettlementRevisionView.State.APPLIED,
            3,
            CREATED,
            CREATED.plusSeconds(5),
            CREATED.plusSeconds(5),
            null);

    assertThat(SettlementRevisionProof.verify(REVISION, pending)).isSameAs(pending);
    assertThat(SettlementRevisionProof.verify(REVISION, applied)).isSameAs(applied);
  }

  private static SettlementRevisionView view(
      UUID revisionId,
      Long revisionNumber,
      SettlementRevisionView.State state,
      Integer attemptCount,
      Instant createdAt,
      Instant updatedAt,
      Instant appliedAt,
      Long walletQueueSequence) {
    return new SettlementRevisionView(
        revisionId,
        UUID.fromString("018f0000-0000-7000-8000-000000000153"),
        revisionNumber,
        UUID.fromString("018f0000-0000-7000-8000-000000000154"),
        UUID.fromString("018f0000-0000-7000-8000-000000000155"),
        state,
        attemptCount,
        state == SettlementRevisionView.State.PENDING
                || state == SettlementRevisionView.State.BLOCKED
            ? createdAt
            : null,
        null,
        null,
        null,
        walletQueueSequence,
        null,
        null,
        null,
        null,
        createdAt,
        updatedAt,
        appliedAt);
  }
}
