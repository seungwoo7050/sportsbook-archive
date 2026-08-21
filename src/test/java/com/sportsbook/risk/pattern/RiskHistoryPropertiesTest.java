package com.sportsbook.risk.pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RiskHistoryPropertiesTest {
  @Test
  void suppliesBoundedHistoryDefaults() {
    RiskHistoryProperties properties = new RiskHistoryProperties(null, 0);

    assertThat(properties.idleRetention()).isEqualTo(Duration.ofDays(7));
    assertThat(properties.maxStakeSamples()).isEqualTo(100);
  }

  @Test
  void rejectsInvalidHistoryBounds() {
    assertThatThrownBy(() -> new RiskHistoryProperties(Duration.ZERO, 10))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new RiskHistoryProperties(Duration.ofDays(1), -1))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
