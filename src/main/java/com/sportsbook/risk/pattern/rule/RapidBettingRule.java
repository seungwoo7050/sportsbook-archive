package com.sportsbook.risk.pattern.rule;

import com.sportsbook.risk.pattern.PatternContext;
import com.sportsbook.risk.pattern.PatternMatch;
import com.sportsbook.risk.pattern.PatternRule;
import com.sportsbook.risk.policy.RapidBettingPolicy;
import com.sportsbook.risk.policy.SafeRedisNumber;
import com.sportsbook.risk.snapshot.PatternSnapshot;
import java.util.Objects;
import java.util.Optional;

/** Detects a candidate that reaches the configured rapid-bet threshold. */
public final class RapidBettingRule implements PatternRule {
  public static final String NAME = "RAPID_BETTING";
  private static final int PRIORITY = 10;
  private final RapidBettingPolicy policy;

  public RapidBettingRule(RapidBettingPolicy policy) {
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
    long candidateCount =
        SafeRedisNumber.add(snapshot.recentBetCount().valueOrThrow(), 1, "rapid bet count");
    if (candidateCount < policy.maxBets()) {
      return Optional.empty();
    }
    return Optional.of(new PatternMatch(NAME, policy.action(), "rapid betting threshold reached"));
  }
}
