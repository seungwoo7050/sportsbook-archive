package com.sportsbook.risk.pattern.rule;

import com.sportsbook.protocol.value.SelectionId;
import com.sportsbook.risk.pattern.PatternContext;
import com.sportsbook.risk.pattern.PatternMatch;
import com.sportsbook.risk.pattern.PatternRule;
import com.sportsbook.risk.policy.RepeatedSelectionPolicy;
import com.sportsbook.risk.policy.SafeRedisNumber;
import com.sportsbook.risk.snapshot.PatternSnapshot;
import java.util.Objects;
import java.util.Optional;

/** Detects the first candidate selection that exceeds its global repeat cap. */
public final class RepeatedSameSelectionRule implements PatternRule {
  public static final String NAME = "REPEATED_SAME_SELECTION";
  private static final int PRIORITY = 30;

  private final RepeatedSelectionPolicy policy;

  public RepeatedSameSelectionRule(RepeatedSelectionPolicy policy) {
    this.policy = Objects.requireNonNull(policy, "policy");
  }

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public int priority() {
    return PRIORITY;
  }

  @Override
  public Optional<PatternMatch> evaluate(PatternContext context, PatternSnapshot snapshot) {
    Objects.requireNonNull(context, "context");
    Objects.requireNonNull(snapshot, "snapshot");
    if (!policy.enabled()) {
      return Optional.empty();
    }
    for (SelectionId selectionId : context.selections()) {
      long candidateCount =
          SafeRedisNumber.add(
              snapshot.selectionCount(selectionId).valueOrThrow(), 1, "selection count");
      if (candidateCount > policy.maxCount()) {
        return Optional.of(
            new PatternMatch(
                NAME, policy.action(), "repeated selection threshold reached: " + selectionId));
      }
    }
    return Optional.empty();
  }
}
