package com.sportsbook.risk.service;

import com.sportsbook.risk.event.RiskSignalPublisher;
import com.sportsbook.risk.pattern.RuleEngine;
import com.sportsbook.risk.pattern.rule.RapidBettingRule;
import com.sportsbook.risk.pattern.rule.RepeatedSameSelectionRule;
import com.sportsbook.risk.pattern.rule.SuddenStakeIncreaseRule;
import com.sportsbook.risk.policy.RiskLimitProperties;
import com.sportsbook.risk.policy.RiskPatternProperties;
import com.sportsbook.risk.snapshot.RiskSnapshotReader;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Composes deterministic diagnostic policy collaborators. */
@Configuration
public class RiskPolicyConfiguration {
  @Bean
  RuleEngine riskRuleEngine(RiskPatternProperties patterns) {
    return new RuleEngine(
        List.of(
            new RapidBettingRule(patterns.rapidBetting()),
            new SuddenStakeIncreaseRule(patterns.suddenStake()),
            new RepeatedSameSelectionRule(patterns.repeatedSelection())));
  }

  @Bean
  RiskCheckService riskCheckService(
      RiskLimitProperties limits,
      RiskSnapshotReader snapshots,
      RuleEngine rules,
      RiskSignalPublisher signals,
      MeterRegistry meters) {
    return new RiskCheckService(limits, snapshots, rules, signals, meters);
  }
}
