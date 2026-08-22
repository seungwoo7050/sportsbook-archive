package com.sportsbook.settlement.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class SettlementRuntimePropertiesTest {

  @Test
  void suppliesSafeDefaults() {
    SettlementRuntimeProperties properties = new SettlementRuntimeProperties(null, null, null, 0);

    assertThat(properties.correctionWindow()).isEqualTo(Duration.ofHours(24));
    assertThat(properties.batchSize()).isEqualTo(100);
  }

  @Test
  void rejectsUnsafeBounds() {
    assertThatThrownBy(
            () -> new SettlementRuntimeProperties(null, Duration.ZERO, null, 1001))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
