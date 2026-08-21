package com.sportsbook.risk.service;

import com.sportsbook.risk.pattern.PatternMatch;
import com.sportsbook.risk.policy.PatternAction;
import java.util.List;
import java.util.Objects;

/** Diagnostic result containing one limit rejection and every ordered pattern signal. */
public record RiskCheckOutcome(
    boolean approved, LimitRejection rejection, List<PatternMatch> patterns) {
  public RiskCheckOutcome {
    Objects.requireNonNull(patterns, "patterns");
    patterns = List.copyOf(patterns);
    boolean blocked = patterns.stream().anyMatch(match -> match.action() == PatternAction.BLOCK);
    if (approved && (rejection != null || blocked)) {
      throw new IllegalArgumentException("approved outcome contains a blocking decision");
    }
    if (!approved && rejection == null && !blocked) {
      throw new IllegalArgumentException("rejected outcome has no blocking decision");
    }
  }

  public static RiskCheckOutcome approved(List<PatternMatch> patterns) {
    return new RiskCheckOutcome(true, null, patterns);
  }

  public static RiskCheckOutcome rejectedByLimit(LimitRejection rejection) {
    return new RiskCheckOutcome(false, Objects.requireNonNull(rejection, "rejection"), List.of());
  }

  public static RiskCheckOutcome rejectedByPattern(List<PatternMatch> patterns) {
    return new RiskCheckOutcome(false, null, patterns);
  }
}
