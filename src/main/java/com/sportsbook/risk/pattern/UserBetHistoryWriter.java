package com.sportsbook.risk.pattern;

/** Projects one accepted candidate into bounded confirmed pattern history. */
public interface UserBetHistoryWriter {
  WriteResult record(PatternContext context);

  record WriteResult(boolean betAdded, boolean stakeAdded) {}
}
