package com.sportsbook.risk.snapshot;

import com.sportsbook.risk.policy.SafeRedisNumber;

/** Canonical integer parser for values kept as strings across Redis JSON. */
final class RiskWireNumbers {
  private RiskWireNumbers() {}

  static long exact(String raw, String name) {
    if (raw == null || !raw.matches("0|[1-9][0-9]*")) {
      throw malformed(name);
    }
    try {
      return SafeRedisNumber.requireNonNegative(Long.parseLong(raw), name);
    } catch (IllegalArgumentException failure) {
      throw new IllegalStateException("malformed snapshot integer: " + name, failure);
    }
  }

  static IllegalStateException malformed(String name) {
    return new IllegalStateException("malformed snapshot field: " + name);
  }
}
