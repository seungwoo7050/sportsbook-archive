package com.sportsbook.risk.snapshot;

import java.util.Objects;

/** One atomic Redis view used by diagnostics and pure policy evaluation. */
public record RiskSnapshot(LimitSnapshot limits, PatternSnapshot patterns) {
  public RiskSnapshot {
    Objects.requireNonNull(limits, "limits");
    Objects.requireNonNull(patterns, "patterns");
  }
}
