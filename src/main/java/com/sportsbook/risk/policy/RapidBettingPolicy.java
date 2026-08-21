package com.sportsbook.risk.policy;

import java.time.Duration;

/** Threshold for bursts of accepted and currently reserved bets. */
public record RapidBettingPolicy(
    boolean enabled, Duration window, int maxBets, PatternAction action) {

  private static final Duration DEFAULT_WINDOW = Duration.ofMinutes(1);
  private static final int DEFAULT_MAX_BETS = 30;

  public RapidBettingPolicy {
    window = window == null ? DEFAULT_WINDOW : window;
    maxBets = maxBets == 0 ? DEFAULT_MAX_BETS : maxBets;
    action = action == null ? PatternAction.SUSPECT : action;
    if (enabled && (window.isZero() || window.isNegative())) {
      throw new IllegalArgumentException("rapid betting window must be positive");
    }
    if (enabled && maxBets <= 0) {
      throw new IllegalArgumentException("rapid betting max-bets must be positive");
    }
  }

  public static RapidBettingPolicy defaults() {
    return new RapidBettingPolicy(false, DEFAULT_WINDOW, DEFAULT_MAX_BETS, PatternAction.SUSPECT);
  }
}
