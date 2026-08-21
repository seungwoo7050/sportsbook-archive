package com.sportsbook.risk.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.risk.pattern.PatternMatch;
import com.sportsbook.risk.policy.PatternAction;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReservationWireValidatorTest {
  private static final String TOKEN = "a".repeat(64);
  private static final List<PatternMatch> PATTERNS =
      List.of(new PatternMatch("rapid-betting", PatternAction.SUSPECT, "rapid"));

  @Test
  void validatesApprovedRejectedAndConflictShapes() {
    ReservationDecision approved =
        ReservationWireValidator.decision(
            new ReservationWire("1", "0", "APPROVED", "RESERVED", "10", TOKEN, false, null, "[]"),
            PATTERNS);
    ReservationDecision rejected =
        ReservationWireValidator.decision(
            new ReservationWire("1", "0", "REJECTED", null, null, null, true, "LIMIT", "[]"),
            PATTERNS);
    ReservationDecision conflict =
        ReservationWireValidator.decision(
            new ReservationWire("1", "0", "CONFLICT", null, null, null, false, null, null),
            List.of());

    assertThat(approved.state()).isEqualTo(ReservationState.RESERVED);
    assertThat(approved.expiresAt()).isEqualTo(Instant.ofEpochMilli(10));
    assertThat(approved.token()).isEqualTo(TOKEN);
    assertThat(approved.patterns()).isEqualTo(PATTERNS);
    assertThat(rejected.rejection()).isEqualTo("LIMIT");
    assertThat(rejected.replayed()).isTrue();
    assertThat(conflict.status()).isEqualTo(ReservationDecision.Status.CONFLICT);
  }

  @Test
  void rejectsMixedOrNoncanonicalShapes() {
    assertThatThrownBy(
            () ->
                ReservationWireValidator.decision(
                    new ReservationWire(
                        "1", "0", "APPROVED", "RESERVED", "01", TOKEN, false, null, "[]"),
                    List.of()))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(
            () ->
                ReservationWireValidator.decision(
                    new ReservationWire(
                        "1", "0", "REJECTED", "RESERVED", null, null, false, "LIMIT", "[]"),
                    List.of()))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(
            () ->
                ReservationWireValidator.decision(
                    new ReservationWire("1", "0", "CONFLICT", null, null, TOKEN, false, null, null),
                    List.of()))
        .isInstanceOf(IllegalStateException.class);
  }
}
