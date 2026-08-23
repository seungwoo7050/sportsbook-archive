package com.sportsbook.admin.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
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

  @Test
  void rejectsMismatchedUnsafeAndImpossibleLifecycleProofs() {
    UUID other = UUID.fromString("018f0000-0000-7000-8000-000000000152");
    List<SettlementRevisionView> invalid =
        List.of(
            view(other, 1L, SettlementRevisionView.State.PENDING, 0, CREATED, CREATED, null, null),
            view(
                REVISION,
                0L,
                SettlementRevisionView.State.PENDING,
                0,
                CREATED,
                CREATED,
                null,
                null),
            view(
                REVISION,
                1L,
                SettlementRevisionView.State.PENDING,
                13,
                CREATED,
                CREATED,
                null,
                null),
            view(
                REVISION,
                1L,
                SettlementRevisionView.State.PENDING,
                0,
                CREATED,
                CREATED.minusSeconds(1),
                null,
                null),
            view(
                REVISION,
                1L,
                SettlementRevisionView.State.APPLIED,
                1,
                CREATED,
                CREATED,
                null,
                null),
            view(
                REVISION,
                1L,
                SettlementRevisionView.State.PENDING,
                0,
                CREATED,
                CREATED,
                CREATED,
                null),
            view(
                REVISION,
                1L,
                SettlementRevisionView.State.BLOCKED,
                1,
                CREATED,
                CREATED,
                null,
                -1L));

    invalid.forEach(
        view ->
            assertThatThrownBy(() -> SettlementRevisionProof.verify(REVISION, view))
                .isInstanceOf(DownstreamContractException.class));
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
