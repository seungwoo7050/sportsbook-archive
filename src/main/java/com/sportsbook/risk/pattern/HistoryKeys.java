package com.sportsbook.risk.pattern;

import com.sportsbook.protocol.value.BetId;
import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.SelectionId;
import com.sportsbook.protocol.value.UserId;
import com.sportsbook.risk.policy.SafeRedisNumber;
import java.util.Objects;

/** Redis keys for confirmed pattern history owned by one user hash slot. */
public final class HistoryKeys {
  private static final String PREFIX = "risk:history:";

  private HistoryKeys() {}

  public static String bets(UserId userId) {
    return base(userId) + ":bets";
  }

  public static String stakes(UserId userId, Currency currency) {
    return base(userId)
        + ":stakes:"
        + Objects.requireNonNull(currency, "currency").name().toLowerCase();
  }

  public static String selection(UserId userId, SelectionId selectionId) {
    Objects.requireNonNull(selectionId, "selectionId");
    return base(userId) + ":selection:" + selectionId.value();
  }

  public static String betMember(BetId betId) {
    return Objects.requireNonNull(betId, "betId").value().toString();
  }

  public static String stakeMember(BetId betId, long amount) {
    SafeRedisNumber.requirePositive(amount, "amount");
    return betMember(betId) + "|" + amount;
  }

  private static String base(UserId userId) {
    Objects.requireNonNull(userId, "userId");
    return PREFIX + "{" + userId.value() + "}";
  }
}
