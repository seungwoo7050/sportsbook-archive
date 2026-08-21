package com.sportsbook.risk.pattern.rule;

import com.sportsbook.risk.pattern.PatternContext;
import com.sportsbook.risk.pattern.PatternMatch;
import com.sportsbook.risk.pattern.PatternRule;
import com.sportsbook.risk.policy.SafeRedisNumber;
import com.sportsbook.risk.policy.SuddenStakePolicy;
import com.sportsbook.risk.snapshot.PatternSnapshot;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Detects a large candidate relative to the median recent same-currency stake. */
public final class SuddenStakeIncreaseRule implements PatternRule {
  public static final String NAME = "SUDDEN_STAKE_INCREASE";
  private static final int PRIORITY = 20;
  private static final BigInteger TWO = BigInteger.valueOf(2);

  private final SuddenStakePolicy policy;

  public SuddenStakeIncreaseRule(SuddenStakePolicy policy) {
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
    List<Long> recent = snapshot.recentStakes().valueOrThrow();
    if (recent.size() < policy.lookbackBets()) {
      return Optional.empty();
    }
    List<Long> sample =
        new ArrayList<>(recent.subList(recent.size() - policy.lookbackBets(), recent.size()));
    sample.forEach(value -> SafeRedisNumber.requireNonNegative(value, "recent stake"));
    sample.sort(Long::compareTo);
    BigInteger doubledMedian = doubledMedian(sample);
    if (doubledMedian.signum() == 0) {
      return Optional.empty();
    }
    BigInteger candidate = BigInteger.valueOf(context.stake().amount()).multiply(TWO);
    BigInteger threshold = doubledMedian.multiply(BigInteger.valueOf(policy.multiplier()));
    return candidate.compareTo(threshold) < 0
        ? Optional.empty()
        : Optional.of(new PatternMatch(NAME, policy.action(), "sudden stake threshold reached"));
  }

  private static BigInteger doubledMedian(List<Long> sorted) {
    int middle = sorted.size() / 2;
    if (sorted.size() % 2 == 1) {
      return BigInteger.valueOf(sorted.get(middle)).multiply(TWO);
    }
    return BigInteger.valueOf(sorted.get(middle - 1)).add(BigInteger.valueOf(sorted.get(middle)));
  }
}
