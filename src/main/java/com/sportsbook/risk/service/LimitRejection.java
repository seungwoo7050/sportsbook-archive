package com.sportsbook.risk.service;

import com.sportsbook.protocol.value.Currency;
import com.sportsbook.risk.counter.LimitType;
import com.sportsbook.risk.policy.SafeRedisNumber;
import java.util.Objects;

/** The first configured capacity exceeded by one diagnostic candidate. */
public record LimitRejection(
    String reason, LimitType type, Currency currency, long current, long limit, long requested) {
  public LimitRejection {
    if (reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("reason must not be blank");
    }
    if (type == null && !"SINGLE_BET_MAX_EXCEEDED".equals(reason)) {
      throw new IllegalArgumentException("only the single-bet limit has no rolling type");
    }
    if (type != null && !reason.equals(type.name() + "_LIMIT_EXCEEDED")) {
      throw new IllegalArgumentException("reason does not match the rolling type");
    }
    if (type == null || type.currencyScoped()) {
      Objects.requireNonNull(currency, "currency");
    } else if (currency != null) {
      throw new IllegalArgumentException("count rejection must not contain currency");
    }
    SafeRedisNumber.requireNonNegative(current, "current");
    SafeRedisNumber.requireNonNegative(limit, "limit");
    SafeRedisNumber.requirePositive(requested, "requested");
    if (current <= limit && requested <= limit - current) {
      throw new IllegalArgumentException("candidate does not exceed the limit");
    }
  }

  public static LimitRejection single(Currency currency, long limit, long requested) {
    return new LimitRejection("SINGLE_BET_MAX_EXCEEDED", null, currency, 0L, limit, requested);
  }

  public static LimitRejection rolling(
      LimitType type, Currency currency, long current, long limit, long requested) {
    Objects.requireNonNull(type, "type");
    return new LimitRejection(
        type.name() + "_LIMIT_EXCEEDED", type, currency, current, limit, requested);
  }
}
