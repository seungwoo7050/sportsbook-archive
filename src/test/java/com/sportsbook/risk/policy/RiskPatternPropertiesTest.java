package com.sportsbook.risk.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RiskPatternPropertiesTest {
  @Test
  void fillsAnEntirelyOmittedPatternPolicy() {
    RiskPatternProperties properties = new RiskPatternProperties(null, null, null);

    assertThat(properties.rapidBetting()).isEqualTo(RapidBettingPolicy.defaults());
    assertThat(properties.suddenStake()).isEqualTo(SuddenStakePolicy.defaults());
    assertThat(properties.repeatedSelection()).isEqualTo(RepeatedSelectionPolicy.defaults());
  }

  @Test
  void preservesConfiguredRulesWhileFillingMissingOnes() {
    var rapid = new RapidBettingPolicy(true, Duration.ofSeconds(10), 3, PatternAction.BLOCK);
    RiskPatternProperties properties = new RiskPatternProperties(rapid, null, null);

    assertThat(properties.rapidBetting()).isSameAs(rapid);
    assertThat(properties.suddenStake()).isEqualTo(SuddenStakePolicy.defaults());
    assertThat(properties.repeatedSelection()).isEqualTo(RepeatedSelectionPolicy.defaults());
  }
}
