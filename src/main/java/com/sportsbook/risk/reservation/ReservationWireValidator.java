package com.sportsbook.risk.reservation;

import com.sportsbook.risk.pattern.PatternMatch;
import com.sportsbook.risk.policy.SafeRedisNumber;
import java.time.Instant;
import java.util.List;

/** Validates mutually exclusive reservation result shapes after JSON decoding. */
final class ReservationWireValidator {
  private ReservationWireValidator() {}

  static ReservationDecision decision(ReservationWire wire, List<PatternMatch> patterns) {
    return switch (wire.status()) {
      case "APPROVED" -> approved(wire, patterns);
      case "REJECTED" -> rejected(wire, patterns);
      case "CONFLICT" -> conflict(wire);
      default -> throw malformed();
    };
  }

  static long exact(String value, String name) {
    if (value == null || !value.matches("0|[1-9][0-9]*")) {
      throw malformed();
    }
    try {
      return SafeRedisNumber.requireNonNegative(Long.parseLong(value), name);
    } catch (IllegalArgumentException failure) {
      throw malformed(failure);
    }
  }

  private static ReservationDecision approved(ReservationWire wire, List<PatternMatch> patterns) {
    if (wire.rejection() != null || wire.patternsJson() == null || !token(wire.token())) {
      throw malformed();
    }
    ReservationState state;
    try {
      state = ReservationState.valueOf(wire.state());
    } catch (RuntimeException failure) {
      throw malformed(failure);
    }
    return ReservationDecision.approved(
        state,
        Instant.ofEpochMilli(exact(wire.expiresAt(), "expiresAt")),
        wire.token(),
        wire.replayed(),
        patterns);
  }

  private static ReservationDecision rejected(ReservationWire wire, List<PatternMatch> patterns) {
    if (wire.state() != null
        || wire.expiresAt() != null
        || wire.token() != null
        || wire.rejection() == null
        || wire.rejection().isBlank()
        || wire.patternsJson() == null) {
      throw malformed();
    }
    return ReservationDecision.rejected(wire.rejection(), wire.replayed(), patterns);
  }

  private static ReservationDecision conflict(ReservationWire wire) {
    if (wire.replayed()
        || wire.state() != null
        || wire.expiresAt() != null
        || wire.token() != null
        || wire.rejection() != null
        || wire.patternsJson() != null) {
      throw malformed();
    }
    return ReservationDecision.conflict();
  }

  private static boolean token(String value) {
    return value != null && value.matches("[0-9a-f]{64}");
  }

  static IllegalStateException malformed() {
    return new IllegalStateException("malformed reservation result");
  }

  static IllegalStateException malformed(Throwable cause) {
    return new IllegalStateException("malformed reservation result", cause);
  }
}
