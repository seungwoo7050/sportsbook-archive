package com.sportsbook.risk.pattern;

import com.sportsbook.risk.snapshot.PatternSnapshot;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Evaluates every configured rule in an explicit deterministic order. */
public final class RuleEngine {
  private final List<PatternRule> rules;

  public RuleEngine(List<PatternRule> rules) {
    Objects.requireNonNull(rules, "rules");
    if (rules.stream().anyMatch(Objects::isNull)) {
      throw new IllegalArgumentException("rules must not contain null");
    }
    if (new HashSet<>(rules.stream().map(PatternRule::name).toList()).size() != rules.size()) {
      throw new IllegalArgumentException("rule names must be unique");
    }
    this.rules =
        rules.stream()
            .sorted(Comparator.comparingInt(PatternRule::priority).thenComparing(PatternRule::name))
            .toList();
  }

  public List<PatternMatch> evaluate(PatternContext context, PatternSnapshot snapshot) {
    Objects.requireNonNull(context, "context");
    Objects.requireNonNull(snapshot, "snapshot");
    return rules.stream().flatMap(rule -> rule.evaluate(context, snapshot).stream()).toList();
  }

  public List<String> ruleOrder() {
    return rules.stream().map(PatternRule::name).toList();
  }
}
