package com.sportsbook.risk.policy;

import com.sportsbook.protocol.value.Currency;
import com.sportsbook.risk.counter.LimitType;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Default user limits used when no administrative override exists. */
@ConfigurationProperties(prefix = "risk.limits")
public record RiskLimitProperties(
    Map<Currency, Long> stakeDaily,
    Map<Currency, Long> stakeWeekly,
    Map<Currency, Long> stakeMonthly,
    Map<Currency, Long> singleBetMax,
    int selectionsPerMinute) {

  private static final Map<Currency, Long> DAILY = defaults(1_000_000L, 100_000L);
  private static final Map<Currency, Long> WEEKLY = defaults(5_000_000L, 500_000L);
  private static final Map<Currency, Long> MONTHLY = defaults(20_000_000L, 2_000_000L);
  private static final Map<Currency, Long> SINGLE = defaults(500_000L, 50_000L);
  private static final int DEFAULT_SELECTIONS_PER_MINUTE = 30;

  public RiskLimitProperties {
    stakeDaily = normalize(stakeDaily, DAILY, "stake-daily");
    stakeWeekly = normalize(stakeWeekly, WEEKLY, "stake-weekly");
    stakeMonthly = normalize(stakeMonthly, MONTHLY, "stake-monthly");
    singleBetMax = normalize(singleBetMax, SINGLE, "single-bet-max");
    if (selectionsPerMinute == 0) {
      selectionsPerMinute = DEFAULT_SELECTIONS_PER_MINUTE;
    }
    SafeRedisNumber.requirePositive(selectionsPerMinute, "selections-per-minute");
  }

  public long singleBetMax(Currency currency) {
    return required(singleBetMax, currency);
  }

  public long limit(LimitType type, Currency currency) {
    return switch (type) {
      case STAKE_DAILY -> required(stakeDaily, currency);
      case STAKE_WEEKLY -> required(stakeWeekly, currency);
      case STAKE_MONTHLY -> required(stakeMonthly, currency);
      case SELECTIONS_PER_MINUTE -> selectionsPerMinute;
    };
  }

  private static Map<Currency, Long> normalize(
      Map<Currency, Long> source, Map<Currency, Long> fallback, String name) {
    EnumMap<Currency, Long> result = new EnumMap<>(Currency.class);
    result.putAll(source == null || source.isEmpty() ? fallback : source);
    for (Currency currency : Currency.values()) {
      Long value = result.get(currency);
      if (value == null) {
        value = fallback.get(currency);
        result.put(currency, value);
      }
      SafeRedisNumber.requireNonNegative(value, name + "." + currency.name());
    }
    return Map.copyOf(result);
  }

  private static Map<Currency, Long> defaults(long krw, long usd) {
    return Map.of(Currency.KRW, krw, Currency.USD, usd);
  }

  private static long required(Map<Currency, Long> values, Currency currency) {
    return Objects.requireNonNull(values.get(currency), "missing limit for " + currency);
  }
}
