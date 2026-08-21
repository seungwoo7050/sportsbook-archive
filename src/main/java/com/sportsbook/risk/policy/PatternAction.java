package com.sportsbook.risk.policy;

/** Operator-selected consequence when a suspicious activity rule matches. */
public enum PatternAction {
  SUSPECT,
  REVIEW,
  BLOCK
}
