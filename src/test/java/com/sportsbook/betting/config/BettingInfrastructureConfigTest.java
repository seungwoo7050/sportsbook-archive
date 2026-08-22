package com.sportsbook.betting.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import org.junit.jupiter.api.Test;

class BettingInfrastructureConfigTest {

  @Test
  void providesUtcClock() {
    Clock clock = new BettingInfrastructureConfig().clock();

    assertThat(BettingInfrastructureConfig.isUtc(clock)).isTrue();
  }
}
