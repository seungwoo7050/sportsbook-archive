package com.sportsbook.risk.reservation;

import com.sportsbook.protocol.value.BetId;
import com.sportsbook.risk.counter.LimitType;
import com.sportsbook.risk.pattern.RiskHistoryProperties;
import com.sportsbook.risk.policy.RiskPatternProperties;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Typed key and argument boundary for commit and release lifecycle scripts. */
public record ReservationTransitionRequest(List<String> keys, List<String> arguments) {
  public ReservationTransitionRequest {
    keys = List.copyOf(Objects.requireNonNull(keys, "keys"));
    arguments = List.copyOf(Objects.requireNonNull(arguments, "arguments"));
  }

  public static ReservationTransitionRequest commit(
      BetId betId,
      String token,
      Instant now,
      RiskReservationProperties reservations,
      RiskPatternProperties patterns,
      RiskHistoryProperties history) {
    requireToken(token);
    Objects.requireNonNull(now, "now");
    return new ReservationTransitionRequest(
        keys(betId),
        List.of(
            "1",
            Long.toString(now.toEpochMilli()),
            Long.toString(reservations.retention().toMillis()),
            token,
            Long.toString(LimitType.STAKE_DAILY.window().toMillis()),
            Long.toString(LimitType.STAKE_WEEKLY.window().toMillis()),
            Long.toString(LimitType.STAKE_MONTHLY.window().toMillis()),
            Long.toString(LimitType.SELECTIONS_PER_MINUTE.window().toMillis()),
            Long.toString(patterns.rapidBetting().window().toMillis()),
            Long.toString(patterns.repeatedSelection().window().toMillis()),
            Long.toString(history.idleRetention().toMillis()),
            Integer.toString(history.maxStakeSamples())));
  }

  public static ReservationTransitionRequest release(
      BetId betId, Instant now, RiskReservationProperties reservations) {
    Objects.requireNonNull(now, "now");
    return new ReservationTransitionRequest(
        keys(betId),
        List.of(
            "1",
            Long.toString(now.toEpochMilli()),
            Long.toString(reservations.retention().toMillis())));
  }

  private static List<String> keys(BetId betId) {
    return List.of(ReservationKeys.lifecycle(betId), ReservationKeys.ACTIVE_COUNT);
  }

  private static void requireToken(String token) {
    if (token == null || !token.matches("[0-9a-f]{64}")) {
      throw new IllegalArgumentException(
          "reservation token must be a 64-character lowercase hex value");
    }
  }
}
