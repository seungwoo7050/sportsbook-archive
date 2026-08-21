package com.sportsbook.risk.limit;

import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.UserId;
import com.sportsbook.risk.counter.LimitType;
import com.sportsbook.risk.policy.RiskLimitProperties;
import java.util.Objects;

/** Resolves a user override before the deployed default policy. */
public final class LimitResolver {
  private final RiskLimitProperties defaults;
  private final LimitOverrideStore overrides;

  public LimitResolver(RiskLimitProperties defaults, LimitOverrideStore overrides) {
    this.defaults = Objects.requireNonNull(defaults, "defaults");
    this.overrides = Objects.requireNonNull(overrides, "overrides");
  }

  public long resolve(UserId userId, LimitType type, Currency currency) {
    Objects.requireNonNull(userId, "userId");
    Objects.requireNonNull(type, "type");
    LimitOverrideField field =
        type.currencyScoped()
            ? LimitOverrideField.monetary(type, Objects.requireNonNull(currency, "currency"))
            : LimitOverrideField.selections();
    return overrides.find(userId, field).orElseGet(() -> defaults.limit(type, currency));
  }
}
