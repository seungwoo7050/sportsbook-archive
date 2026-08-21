package com.sportsbook.risk.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SuddenStakePolicyTest {
  @Test
  void suppliesConservativeDisabledDefaults() {
    SuddenStakePolicy policy = SuddenStakePolicy.defaults();

    assertThat(policy.enabled()).isFalse();
    assertThat(policy.multiplier()).isEqualTo(10);
    assertThat(policy.lookbackBets()).isEqualTo(10);
    assertThat(policy.action()).isEqualTo(PatternAction.SUSPECT);
  }

  @Test
  void validatesEnabledThresholds() {
    assertThatThrownBy(() -> new SuddenStakePolicy(true, 1, 10, PatternAction.BLOCK))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new SuddenStakePolicy(true, 2, -1, PatternAction.BLOCK))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
