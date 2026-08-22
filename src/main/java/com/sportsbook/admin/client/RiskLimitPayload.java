package com.sportsbook.admin.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sportsbook.protocol.value.Currency;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RiskLimitPayload(RiskLimitType type, Currency currency, Long value) {

  public static final long MAX_SAFE_VALUE = 9_007_199_254_740_991L;

  public RiskLimitPayload {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(value, "value");
    if (value < 0 || value > MAX_SAFE_VALUE) {
      throw new IllegalArgumentException("Risk limit value is outside the safe range");
    }
    if (type.requiresCurrency() != (currency != null)) {
      throw new IllegalArgumentException("Risk limit currency scope does not match its type");
    }
  }
}
