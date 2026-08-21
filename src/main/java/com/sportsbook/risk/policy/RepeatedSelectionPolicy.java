package com.sportsbook.risk.policy;

import java.time.Duration;

/** Threshold for repeatedly betting the same selection across currencies. */
public record RepeatedSelectionPolicy(
    boolean enabled, Duration window, int maxCount, PatternAction action) {

  private static final Duration DEFAULT_WINDOW = Duration.ofHours(24);
  private static final int DEFAULT_MAX_COUNT = 5;

  public RepeatedSelectionPolicy {
    window = window == null ? DEFAULT_WINDOW : window;
    maxCount = maxCount == 0 ? DEFAULT_MAX_COUNT : maxCount;
    action = action == null ? PatternAction.REVIEW : action;
    if (enabled && (window.isZero() || window.isNegative())) {
      throw new IllegalArgumentException("repeated selection window must be positive");
    }
    if (enabled && maxCount <= 0) {
      throw new IllegalArgumentException("repeated selection max-count must be positive");
    }
  }

  public static RepeatedSelectionPolicy defaults() {
    return new RepeatedSelectionPolicy(
        false, DEFAULT_WINDOW, DEFAULT_MAX_COUNT, PatternAction.REVIEW);
  }
}
