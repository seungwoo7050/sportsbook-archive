package com.sportsbook.risk.counter;

import com.sportsbook.protocol.value.BetId;
import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.UserId;
import com.sportsbook.risk.policy.SafeRedisNumber;
import java.util.Objects;

/** Redis keys for one user's committed rolling-limit dimension. */
public final class LimitKeys {
  private static final String PREFIX = "risk:limit:";

  private LimitKeys() {}

  public static Keys monetary(UserId userId, LimitType type, Currency currency) {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(currency, "currency");
    if (!type.currencyScoped()) {
      throw new IllegalArgumentException("monetary keys require a currency-scoped limit");
    }
    return keys(userId, type.suffix() + ":" + currency.name().toLowerCase());
  }

  public static Keys selections(UserId userId) {
    return keys(userId, LimitType.SELECTIONS_PER_MINUTE.suffix());
  }

  public static String member(BetId betId, long amount) {
    Objects.requireNonNull(betId, "betId");
    SafeRedisNumber.requirePositive(amount, "amount");
    return betId.value() + "|" + amount;
  }

  private static Keys keys(UserId userId, String dimension) {
    Objects.requireNonNull(userId, "userId");
    String base = PREFIX + "{" + userId.value() + "}:" + dimension;
    return new Keys(base + ":entries", base + ":sum");
  }

  public record Keys(String entries, String sum) {
    public Keys {
      Objects.requireNonNull(entries, "entries");
      Objects.requireNonNull(sum, "sum");
    }
  }
}
