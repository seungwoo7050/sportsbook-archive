package com.sportsbook.risk.pattern;

import com.sportsbook.risk.snapshot.PatternSnapshot;
import java.util.Optional;

/** Pure pattern policy evaluated only from one candidate and one atomic snapshot. */
public interface PatternRule {
  String name();

  int priority();

  Optional<PatternMatch> evaluate(PatternContext context, PatternSnapshot snapshot);
}
