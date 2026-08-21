package com.sportsbook.risk.api;

import com.sportsbook.protocol.value.Currency;
import com.sportsbook.risk.counter.LimitType;
import com.sportsbook.risk.limit.LimitOverrideField;
import java.util.Objects;

/** Identifies one currency-scoped monetary limit or the currency-neutral selection limit. */
public record LimitOverrideTarget(LimitType type, Currency currency) {
  public LimitOverrideTarget {
    Objects.requireNonNull(type, "type");
    if (type.currencyScoped() && currency == null) {
      throw new IllegalArgumentException("currency is required for monetary limits");
    }
    if (!type.currencyScoped() && currency != null) {
      throw new IllegalArgumentException("currency must be omitted for selection limits");
    }
  }

  LimitOverrideField field() {
    return type.currencyScoped()
        ? LimitOverrideField.monetary(type, currency)
        : LimitOverrideField.selections();
  }
}
