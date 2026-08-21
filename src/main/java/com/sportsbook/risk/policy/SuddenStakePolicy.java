package com.sportsbook.risk.policy;

/** Threshold for a candidate stake relative to recent same-currency stakes. */
public record SuddenStakePolicy(
    boolean enabled, int multiplier, int lookbackBets, PatternAction action) {

  private static final int DEFAULT_MULTIPLIER = 10;
  private static final int DEFAULT_LOOKBACK = 10;

  public SuddenStakePolicy {
    multiplier = multiplier == 0 ? DEFAULT_MULTIPLIER : multiplier;
    lookbackBets = lookbackBets == 0 ? DEFAULT_LOOKBACK : lookbackBets;
    action = action == null ? PatternAction.SUSPECT : action;
    if (enabled && multiplier <= 1) {
      throw new IllegalArgumentException("sudden stake multiplier must be greater than one");
    }
    if (enabled && lookbackBets <= 0) {
      throw new IllegalArgumentException("sudden stake lookback must be positive");
    }
  }

  public static SuddenStakePolicy defaults() {
    return new SuddenStakePolicy(
        false, DEFAULT_MULTIPLIER, DEFAULT_LOOKBACK, PatternAction.SUSPECT);
  }
}
