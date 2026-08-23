package com.sportsbook.admin.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SettlementCandidateViewTest {

  private static final UUID CANDIDATE = UUID.fromString("018f0000-0000-7000-8000-000000000141");
  private static final UUID EVENT = UUID.fromString("018f0000-0000-7000-8000-000000000142");
  private static final Instant SETTLED = Instant.parse("2026-08-22T01:00:00Z");
  private static final Instant RECEIVED = Instant.parse("2026-08-22T01:00:01Z");

  @Test
  void acceptsPendingAndDecidedCandidateEvidence() {
    SettlementCandidateView pending =
        view(SettlementCandidateView.State.PENDING, null, null, false);
    SettlementCandidateView accepted =
        view(
            SettlementCandidateView.State.ACCEPTED,
            "approved by operator",
            RECEIVED.plusSeconds(2),
            true);

    assertThat(SettlementCandidateView.verify(CANDIDATE, pending)).isSameAs(pending);
    assertThat(SettlementCandidateView.verify(CANDIDATE, accepted)).isSameAs(accepted);
  }

  @Test
  void rejectsMismatchedIncompleteAndImpossibleLifecycleEvidence() {
    UUID other = UUID.fromString("018f0000-0000-7000-8000-000000000143");
    assertInvalid(
        new SettlementCandidateView(
            other,
            EVENT,
            SettlementCandidateView.Mode.COMPLETED,
            SETTLED,
            RECEIVED,
            SettlementCandidateView.State.PENDING,
            null,
            null,
            null,
            false));
    assertInvalid(view(SettlementCandidateView.State.PENDING, null, RECEIVED, false));
    assertInvalid(view(SettlementCandidateView.State.REJECTED, "bad result", null, false));
    assertInvalid(view(SettlementCandidateView.State.PENDING, null, null, true));
    assertInvalid(
        view(SettlementCandidateView.State.ACCEPTED, "approved", RECEIVED.plusSeconds(2), false));
    assertInvalid(
        new SettlementCandidateView(
            CANDIDATE, null, null, null, null, null, null, null, null, null));
  }

  private static void assertInvalid(SettlementCandidateView view) {
    assertThatThrownBy(() -> SettlementCandidateView.verify(CANDIDATE, view))
        .isInstanceOf(DownstreamContractException.class);
  }

  private static SettlementCandidateView view(
      SettlementCandidateView.State state, String reason, Instant decidedAt, boolean accepted) {
    return new SettlementCandidateView(
        CANDIDATE,
        EVENT,
        SettlementCandidateView.Mode.COMPLETED,
        SETTLED,
        RECEIVED,
        state,
        null,
        reason,
        decidedAt,
        accepted);
  }
}
