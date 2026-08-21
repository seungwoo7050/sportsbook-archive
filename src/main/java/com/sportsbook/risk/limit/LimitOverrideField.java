package com.sportsbook.risk.limit;

import com.sportsbook.protocol.value.Currency;
import com.sportsbook.risk.counter.LimitType;
import java.util.Objects;

/** Canonical Redis hash field for an administrator-set user limit. */
public record LimitOverrideField(LimitType type, Currency currency) {
  public LimitOverrideField {
    Objects.requireNonNull(type, "type");
    if (type.currencyScoped()) {
      Objects.requireNonNull(currency, "currency");
    } else if (currency != null) {
      throw new IllegalArgumentException("selection overrides must be currency-neutral");
    }
  }

  public static LimitOverrideField monetary(LimitType type, Currency currency) {
    return new LimitOverrideField(type, currency);
  }

  public static LimitOverrideField selections() {
    return new LimitOverrideField(LimitType.SELECTIONS_PER_MINUTE, null);
  }

  public String redisField() {
    return currency == null ? type.name() : type.name() + ":" + currency.name();
  }
}
