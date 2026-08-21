package com.sportsbook.risk.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RepeatedSelectionPolicyTest {
  @Test
  void suppliesReviewOrientedDisabledDefaults() {
    RepeatedSelectionPolicy policy = RepeatedSelectionPolicy.defaults();

    assertThat(policy.enabled()).isFalse();
    assertThat(policy.window()).isEqualTo(Duration.ofHours(24));
    assertThat(policy.maxCount()).isEqualTo(5);
    assertThat(policy.action()).isEqualTo(PatternAction.REVIEW);
  }

  @Test
  void validatesEnabledThresholds() {
    assertThatThrownBy(
            () -> new RepeatedSelectionPolicy(true, Duration.ZERO, 1, PatternAction.BLOCK))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> new RepeatedSelectionPolicy(true, Duration.ofSeconds(1), -1, PatternAction.BLOCK))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
