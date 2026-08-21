package com.sportsbook.risk.reservation;

import com.sportsbook.risk.pattern.PatternMatch;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Complete atomic admission result retained for deterministic request replay. */
public record ReservationDecision(
    Status status,
    ReservationState state,
    Instant expiresAt,
    String token,
    String rejection,
    boolean replayed,
    List<PatternMatch> patterns) {
  public ReservationDecision {
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(patterns, "patterns");
    patterns = List.copyOf(patterns);
    switch (status) {
      case APPROVED -> {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(expiresAt, "expiresAt");
        requireText(token, "token");
        if (rejection != null) {
          throw new IllegalArgumentException("approved decision cannot contain a rejection");
        }
      }
      case REJECTED -> {
        requireAbsent(state, expiresAt, token);
        requireText(rejection, "rejection");
      }
      case CONFLICT -> {
        requireAbsent(state, expiresAt, token);
        if (rejection != null || replayed || !patterns.isEmpty()) {
          throw new IllegalArgumentException("conflict decision cannot contain outcome data");
        }
      }
    }
  }

  public static ReservationDecision approved(
      ReservationState state,
      Instant expiresAt,
      String token,
      boolean replayed,
      List<PatternMatch> patterns) {
    return new ReservationDecision(
        Status.APPROVED, state, expiresAt, token, null, replayed, patterns);
  }

  public static ReservationDecision rejected(
      String rejection, boolean replayed, List<PatternMatch> patterns) {
    return new ReservationDecision(
        Status.REJECTED, null, null, null, rejection, replayed, patterns);
  }

  public static ReservationDecision conflict() {
    return new ReservationDecision(Status.CONFLICT, null, null, null, null, false, List.of());
  }

  public boolean approved() {
    return status == Status.APPROVED;
  }

  private static void requireAbsent(Object... values) {
    if (java.util.Arrays.stream(values).anyMatch(Objects::nonNull)) {
      throw new IllegalArgumentException("non-approved decision contains reservation state");
    }
  }

  private static String requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value;
  }

  public enum Status {
    APPROVED,
    REJECTED,
    CONFLICT
  }
}
