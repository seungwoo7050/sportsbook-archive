package com.sportsbook.risk.counter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class LimitTypeTest {
  @Test
  void separatesCurrencyAmountsFromGlobalCounts() {
    assertThat(LimitType.STAKE_DAILY.currencyScoped()).isTrue();
    assertThat(LimitType.STAKE_WEEKLY.currencyScoped()).isTrue();
    assertThat(LimitType.STAKE_MONTHLY.currencyScoped()).isTrue();
    assertThat(LimitType.SELECTIONS_PER_MINUTE.currencyScoped()).isFalse();
  }

  @Test
  void exposesStableSlidingWindows() {
    assertThat(LimitType.STAKE_DAILY.window()).isEqualTo(Duration.ofDays(1));
    assertThat(LimitType.STAKE_WEEKLY.window()).isEqualTo(Duration.ofDays(7));
    assertThat(LimitType.STAKE_MONTHLY.window()).isEqualTo(Duration.ofDays(30));
    assertThat(LimitType.SELECTIONS_PER_MINUTE.window()).isEqualTo(Duration.ofMinutes(1));
  }
}
