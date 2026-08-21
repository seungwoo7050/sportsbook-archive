package com.sportsbook.risk.service;

import com.sportsbook.protocol.value.Currency;
import com.sportsbook.risk.counter.LimitType;
import com.sportsbook.risk.event.RiskSignalPublisher;
import com.sportsbook.risk.pattern.PatternContext;
import com.sportsbook.risk.pattern.RuleEngine;
import com.sportsbook.risk.policy.RiskLimitProperties;
import com.sportsbook.risk.snapshot.LimitSnapshot;
import com.sportsbook.risk.snapshot.RiskSnapshot;
import com.sportsbook.risk.snapshot.RiskSnapshotReader;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import java.util.Objects;

/** Read-only policy diagnostics; betting admission uses the atomic reservation boundary. */
public final class RiskCheckService {
  private static final List<LimitType> MONETARY_LIMITS =
      List.of(LimitType.STAKE_DAILY, LimitType.STAKE_WEEKLY, LimitType.STAKE_MONTHLY);

  private final RiskLimitProperties policy;
  private final RiskSnapshotReader snapshots;
  private final RuleEngine rules;
  private final RiskSignalPublisher signals;
  private final MeterRegistry meters;
  private final Timer latency;

  public RiskCheckService(
      RiskLimitProperties policy,
      RiskSnapshotReader snapshots,
      RuleEngine rules,
      RiskSignalPublisher signals,
      MeterRegistry meters) {
    this.policy = Objects.requireNonNull(policy, "policy");
    this.snapshots = Objects.requireNonNull(snapshots, "snapshots");
    this.rules = Objects.requireNonNull(rules, "rules");
    this.signals = Objects.requireNonNull(signals, "signals");
    this.meters = Objects.requireNonNull(meters, "meters");
    this.latency = Timer.builder("risk.check.latency").register(meters);
  }

  public RiskCheckOutcome check(RiskCheckCommand command) {
    Objects.requireNonNull(command, "command");
    return latency.record(() -> evaluate(command));
  }

  private RiskCheckOutcome evaluate(RiskCheckCommand command) {
    Currency currency = command.stake().currency();
    long requested = command.stake().amount();
    long singleLimit = policy.singleBetMax(currency);
    if (requested > singleLimit) {
      return reject(command, LimitRejection.single(currency, singleLimit, requested));
    }

    RiskSnapshot snapshot = snapshots.read(PatternContext.from(command));
    for (LimitType type : MONETARY_LIMITS) {
      LimitSnapshot.Value value = snapshot.limits().require(type);
      long current = value.current();
      long limit = value.effectiveLimit(policy.limit(type, currency));
      if (exceeds(current, requested, limit)) {
        return reject(command, LimitRejection.rolling(type, currency, current, limit, requested));
      }
    }
    return RiskCheckOutcome.approved(List.of());
  }

  private RiskCheckOutcome reject(RiskCheckCommand command, LimitRejection rejection) {
    meters.counter("risk.limit.violations", "reason", rejection.reason()).increment();
    if (rejection.type() != null) {
      signals.publishLimit(
          command.userId(),
          rejection.type(),
          rejection.current(),
          rejection.limit(),
          command.stake(),
          command.now());
    }
    return RiskCheckOutcome.rejectedByLimit(rejection);
  }

  private static boolean exceeds(long current, long requested, long limit) {
    return current > limit || requested > limit - current;
  }
}
