package com.sportsbook.risk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.sportsbook.risk.event.RiskSignalPublisher;
import com.sportsbook.risk.policy.RiskLimitProperties;
import com.sportsbook.risk.policy.RiskPatternProperties;
import com.sportsbook.risk.snapshot.RiskSnapshotReader;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class RiskPolicyConfigurationTest {
  @Test
  void composesRulesInTheirStablePriorityOrder() {
    RiskPolicyConfiguration configuration = new RiskPolicyConfiguration();
    var rules = configuration.riskRuleEngine(new RiskPatternProperties(null, null, null));

    assertThat(rules.ruleOrder())
        .containsExactly("RAPID_BETTING", "SUDDEN_STAKE_INCREASE", "REPEATED_SAME_SELECTION");
    assertThat(
            configuration.riskCheckService(
                new RiskLimitProperties(null, null, null, null, 0),
                mock(RiskSnapshotReader.class),
                rules,
                mock(RiskSignalPublisher.class),
                new SimpleMeterRegistry()))
        .isNotNull();
  }
}
