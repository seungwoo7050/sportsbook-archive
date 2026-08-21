package com.sportsbook.risk.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sportsbook.protocol.value.Currency;
import com.sportsbook.risk.counter.LimitType;
import com.sportsbook.risk.policy.SafeRedisNumber;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** Exact administrative replacement for one user limit. */
public record LimitUpdateRequest(
    @NotNull LimitType type, Currency currency, @NotNull @PositiveOrZero Long value) {

  @JsonIgnore
  @AssertTrue(message = "currency scope does not match the limit type")
  public boolean hasValidScope() {
    return type == null || type.currencyScoped() == (currency != null);
  }

  @JsonIgnore
  @AssertTrue(message = "limit must be exactly representable")
  public boolean hasExactValue() {
    if (value == null || value < 0) {
      return true;
    }
    try {
      SafeRedisNumber.requireNonNegative(value, "limit");
      return true;
    } catch (IllegalArgumentException exception) {
      return false;
    }
  }

  LimitOverrideTarget target() {
    return new LimitOverrideTarget(type, currency);
  }
}
