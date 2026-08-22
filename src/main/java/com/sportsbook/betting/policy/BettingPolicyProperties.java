package com.sportsbook.betting.policy;

import com.sportsbook.protocol.value.Currency;
import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "betting.policy")
public record BettingPolicyProperties(
    int maxSelections,
    BigDecimal maxTotalOdds,
    Map<Currency, Long> minStake,
    Map<Currency, Long> maxStake,
    BigDecimal slippageTolerancePercent) {

  private static final int DEFAULT_MAX_SELECTIONS = 15;
  private static final BigDecimal DEFAULT_MAX_TOTAL_ODDS = new BigDecimal("10000");
  private static final BigDecimal DEFAULT_SLIPPAGE_PERCENT = new BigDecimal("3");

  public BettingPolicyProperties {
    if (maxSelections < 0 || maxSelections > DEFAULT_MAX_SELECTIONS) {
      throw new IllegalArgumentException("maxSelections must be in 1..15 when configured");
    }
    maxSelections = maxSelections > 0 ? maxSelections : DEFAULT_MAX_SELECTIONS;
    maxTotalOdds =
        maxTotalOdds == null ? DEFAULT_MAX_TOTAL_ODDS : maxTotalOdds.stripTrailingZeros();
    slippageTolerancePercent =
        slippageTolerancePercent == null
            ? DEFAULT_SLIPPAGE_PERCENT
            : slippageTolerancePercent.stripTrailingZeros();
    minStake =
        minStake == null || minStake.isEmpty()
            ? Map.of(Currency.KRW, 1_000L, Currency.USD, 100L)
            : Map.copyOf(minStake);
    maxStake =
        maxStake == null || maxStake.isEmpty()
            ? Map.of(Currency.KRW, 1_000_000L, Currency.USD, 1_000L)
            : Map.copyOf(maxStake);
    if (maxTotalOdds.signum() <= 0 || slippageTolerancePercent.signum() <= 0) {
      throw new IllegalArgumentException("Odds and slippage policy values must be positive");
    }
    if (!minStake.keySet().equals(EnumSet.allOf(Currency.class))
        || !maxStake.keySet().equals(EnumSet.allOf(Currency.class))) {
      throw new IllegalArgumentException("Stake bounds must cover every currency");
    }
    for (Currency currency : Currency.values()) {
      long minimum = minStake.get(currency);
      long maximum = maxStake.get(currency);
      if (minimum <= 0 || maximum <= 0 || minimum > maximum) {
        throw new IllegalArgumentException("Stake bounds must be positive with minimum <= maximum");
      }
    }
  }
}
