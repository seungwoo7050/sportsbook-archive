package com.sportsbook.risk.reservation;

import com.sportsbook.protocol.value.BetId;
import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.SelectionId;
import com.sportsbook.protocol.value.UserId;
import com.sportsbook.risk.counter.LimitKeys;
import java.util.Objects;

/** Redis keys for reservation lifecycle and every active capacity footprint. */
public final class ReservationKeys {
  public static final String ACTIVE_COUNT = "risk:reservations:active";
  private static final String RESERVATION_PREFIX = "risk:reservation:";
  private static final String USER_PREFIX = "risk:reservations:user:";
  private static final String EVENT_PREFIX = "risk:event:fingerprint:";

  private ReservationKeys() {}

  public static String lifecycle(BetId betId) {
    return RESERVATION_PREFIX + required(betId).value();
  }

  public static String activeBets(UserId userId) {
    return userBase(userId) + ":bets";
  }

  public static LimitKeys.Keys activeStakes(UserId userId, Currency currency) {
    String base =
        userBase(userId)
            + ":stakes:"
            + Objects.requireNonNull(currency, "currency").name().toLowerCase();
    return new LimitKeys.Keys(base + ":entries", base + ":sum");
  }

  public static LimitKeys.Keys activeSelections(UserId userId) {
    String base = userBase(userId) + ":selections";
    return new LimitKeys.Keys(base + ":entries", base + ":sum");
  }

  public static String activeSelection(UserId userId, SelectionId selectionId) {
    Objects.requireNonNull(selectionId, "selectionId");
    return userBase(userId) + ":selection:" + selectionId.value();
  }

  public static String acceptedFingerprint(BetId betId) {
    return EVENT_PREFIX + required(betId).value();
  }

  private static String userBase(UserId userId) {
    Objects.requireNonNull(userId, "userId");
    return USER_PREFIX + "{" + userId.value() + "}";
  }

  private static BetId required(BetId betId) {
    return Objects.requireNonNull(betId, "betId");
  }
}
