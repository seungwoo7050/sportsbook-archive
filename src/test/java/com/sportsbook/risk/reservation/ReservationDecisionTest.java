package com.sportsbook.risk.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.risk.pattern.PatternMatch;
import com.sportsbook.risk.policy.PatternAction;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReservationDecisionTest {
  private static final PatternMatch FLAG =
      new PatternMatch("RAPID_BETTING", PatternAction.REVIEW, "matched");

  @Test
  void representsApprovedAndRejectedReplays() {
    List<PatternMatch> flags = new ArrayList<>(List.of(FLAG));
    ReservationDecision approved =
        ReservationDecision.approved(
            ReservationState.RESERVED, Instant.EPOCH.plusSeconds(60), "opaque-token", true, flags);
    flags.clear();

    assertThat(approved.approved()).isTrue();
    assertThat(approved.token()).isEqualTo("opaque-token");
    assertThat(approved.patterns()).containsExactly(FLAG);
    assertThat(approved.replayed()).isTrue();
    assertThat(ReservationDecision.rejected("STAKE_DAILY", true, List.of()).status())
        .isEqualTo(ReservationDecision.Status.REJECTED);
  }

  @Test
  void rejectsInconsistentOutcomeShapes() {
    assertThatThrownBy(
            () ->
                new ReservationDecision(
                    ReservationDecision.Status.APPROVED,
                    ReservationState.RESERVED,
                    Instant.EPOCH,
                    "token",
                    "unexpected",
                    false,
                    List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new ReservationDecision(
                    ReservationDecision.Status.REJECTED, null, null, null, " ", false, List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new ReservationDecision(
                    ReservationDecision.Status.CONFLICT, null, null, null, null, true, List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(ReservationDecision.conflict().approved()).isFalse();
  }
}
