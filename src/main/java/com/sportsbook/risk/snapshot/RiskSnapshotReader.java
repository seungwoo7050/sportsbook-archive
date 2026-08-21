package com.sportsbook.risk.snapshot;

import com.sportsbook.risk.pattern.PatternContext;

/** Reads all diagnostic facts for one candidate in a single Redis operation. */
public interface RiskSnapshotReader {
  RiskSnapshot read(PatternContext context);
}
