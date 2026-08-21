package com.sportsbook.risk.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RiskReservationPropertiesTest {
  @Test
  void suppliesLeaseAndTombstoneDefaults() {
    RiskReservationProperties properties = new RiskReservationProperties(null, null);

    assertThat(properties.lease()).isEqualTo(Duration.ofMinutes(2));
    assertThat(properties.retention()).isEqualTo(Duration.ofDays(32));
  }

  @Test
  void requiresAValidLeaseAndMonthlyTombstone() {
    assertThatThrownBy(() -> new RiskReservationProperties(Duration.ZERO, Duration.ofDays(32)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> new RiskReservationProperties(Duration.ofMinutes(2), Duration.ofDays(30)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("monthly");
    assertThatThrownBy(
            () -> new RiskReservationProperties(Duration.ofDays(40), Duration.ofDays(32)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("lease");
  }
}
