package com.sportsbook.settlement.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class TimeConfigurationTest {

  @Test
  void providesUtcClock() {
    Clock clock = new TimeConfiguration().settlementClock();

    assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
  }
}
