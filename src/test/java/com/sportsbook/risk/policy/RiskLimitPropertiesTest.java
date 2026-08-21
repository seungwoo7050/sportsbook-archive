package com.sportsbook.risk.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.protocol.value.Currency;
import com.sportsbook.risk.counter.LimitType;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RiskLimitPropertiesTest {
  @Test
  void suppliesBothSupportedCurrencyDefaults() {
    RiskLimitProperties properties = properties(null, null, null, null, 0);

    assertThat(properties.limit(LimitType.STAKE_DAILY, Currency.KRW)).isEqualTo(1_000_000L);
    assertThat(properties.limit(LimitType.STAKE_DAILY, Currency.USD)).isEqualTo(100_000L);
    assertThat(properties.limit(LimitType.STAKE_WEEKLY, Currency.KRW)).isEqualTo(5_000_000L);
    assertThat(properties.limit(LimitType.STAKE_MONTHLY, Currency.USD)).isEqualTo(2_000_000L);
    assertThat(properties.singleBetMax(Currency.KRW)).isEqualTo(500_000L);
    assertThat(properties.selectionsPerMinute()).isEqualTo(30);
  }

  @Test
  void fillsMissingCurrenciesWithoutAliasingInputMaps() {
    Map<Currency, Long> daily = new java.util.EnumMap<>(Currency.class);
    daily.put(Currency.KRW, 42L);

    RiskLimitProperties properties = properties(daily, null, null, null, 10);
    daily.put(Currency.KRW, 100L);

    assertThat(properties.limit(LimitType.STAKE_DAILY, Currency.KRW)).isEqualTo(42L);
    assertThat(properties.limit(LimitType.STAKE_DAILY, Currency.USD)).isEqualTo(100_000L);
  }

  @Test
  void rejectsInvalidPolicyAmounts() {
    assertThatThrownBy(() -> properties(Map.of(Currency.KRW, -1L), null, null, null, 10))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                properties(
                    Map.of(Currency.USD, SafeRedisNumber.MAX_VALUE + 1L), null, null, null, 10))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> properties(null, null, null, null, -1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static RiskLimitProperties properties(
      Map<Currency, Long> daily,
      Map<Currency, Long> weekly,
      Map<Currency, Long> monthly,
      Map<Currency, Long> single,
      int selections) {
    return new RiskLimitProperties(daily, weekly, monthly, single, selections);
  }
}
