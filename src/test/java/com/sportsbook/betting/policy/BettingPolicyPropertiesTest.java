package com.sportsbook.betting.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.protocol.value.Currency;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BettingPolicyPropertiesTest {

  @Test
  void suppliesSafeDefaults() {
    BettingPolicyProperties policy = new BettingPolicyProperties(0, null, null, null, null);

    assertThat(policy.maxSelections()).isEqualTo(15);
    assertThat(policy.maxTotalOdds()).isEqualByComparingTo("10000");
    assertThat(policy.minStake()).containsEntry(Currency.KRW, 1_000L);
    assertThat(policy.maxStake()).containsEntry(Currency.USD, 1_000L);
    assertThat(policy.slippageTolerancePercent()).isEqualByComparingTo("3");
  }

  @Test
  void rejectsUnsafeConfiguredLimits() {
    assertThatThrownBy(() -> new BettingPolicyProperties(16, null, null, null, null))
        .hasMessageContaining("1..15");
    assertThatThrownBy(
            () -> new BettingPolicyProperties(15, BigDecimal.ZERO, null, null, new BigDecimal("3")))
        .hasMessageContaining("positive");
    assertThatThrownBy(
            () ->
                new BettingPolicyProperties(
                    15,
                    new BigDecimal("100"),
                    Map.of(Currency.KRW, 2_000L),
                    Map.of(Currency.KRW, 1_000L, Currency.USD, 100L),
                    new BigDecimal("3")))
        .hasMessageContaining("every currency");
    assertThatThrownBy(
            () ->
                new BettingPolicyProperties(
                    15,
                    new BigDecimal("100"),
                    Map.of(Currency.KRW, 2_000L, Currency.USD, 100L),
                    Map.of(Currency.KRW, 1_000L, Currency.USD, 1_000L),
                    new BigDecimal("3")))
        .hasMessageContaining("minimum <= maximum");
  }
}
