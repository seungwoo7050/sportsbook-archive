package com.sportsbook.risk.pattern;

import com.sportsbook.risk.policy.PatternAction;
import java.util.Objects;

/** Stable evidence emitted when one configured rule matches a candidate. */
public record PatternMatch(String rule, PatternAction action, String reason) {
  public PatternMatch {
    rule = requireText(rule, "rule");
    Objects.requireNonNull(action, "action");
    reason = requireText(reason, "reason");
  }

  private static String requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value;
  }
}
