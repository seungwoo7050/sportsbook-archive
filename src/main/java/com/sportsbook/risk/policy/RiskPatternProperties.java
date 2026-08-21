package com.sportsbook.risk.policy;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Complete pattern policy bound as one immutable configuration value. */
@ConfigurationProperties(prefix = "risk.patterns")
public record RiskPatternProperties(
    RapidBettingPolicy rapidBetting,
    SuddenStakePolicy suddenStake,
    RepeatedSelectionPolicy repeatedSelection) {

  public RiskPatternProperties {
    rapidBetting = rapidBetting == null ? RapidBettingPolicy.defaults() : rapidBetting;
    suddenStake = suddenStake == null ? SuddenStakePolicy.defaults() : suddenStake;
    repeatedSelection =
        repeatedSelection == null ? RepeatedSelectionPolicy.defaults() : repeatedSelection;
  }
}
