package com.sportsbook.risk.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RapidBettingPolicyTest {
  @Test
  void suppliesConservativeDisabledDefaults() {
    RapidBettingPolicy policy = RapidBettingPolicy.defaults();

    assertThat(policy.enabled()).isFalse();
    assertThat(policy.window()).isEqualTo(Duration.ofMinutes(1));
    assertThat(policy.maxBets()).isEqualTo(30);
    assertThat(policy.action()).isEqualTo(PatternAction.SUSPECT);
  }

  @Test
  void validatesEnabledThresholds() {
    assertThatThrownBy(() -> new RapidBettingPolicy(true, Duration.ZERO, 1, PatternAction.BLOCK))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> new RapidBettingPolicy(true, Duration.ofSeconds(1), -1, PatternAction.BLOCK))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
