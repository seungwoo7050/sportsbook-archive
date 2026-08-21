package com.sportsbook.risk.limit;

import com.sportsbook.protocol.value.UserId;
import java.util.Objects;

/** Canonical Redis hash key for one user's administrative risk overrides. */
public final class LimitOverrideKeys {
  private static final String PREFIX = "risk:limit:override:";

  private LimitOverrideKeys() {}

  public static String user(UserId userId) {
    Objects.requireNonNull(userId, "userId");
    return PREFIX + "{" + userId.value() + "}";
  }
}
