package com.sportsbook.risk.snapshot;

import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.SelectionId;
import com.sportsbook.protocol.value.UserId;
import com.sportsbook.risk.counter.LimitKeys;
import com.sportsbook.risk.counter.LimitType;
import com.sportsbook.risk.limit.LimitOverrideKeys;
import com.sportsbook.risk.pattern.HistoryKeys;
import com.sportsbook.risk.pattern.PatternContext;
import com.sportsbook.risk.policy.RiskPatternProperties;
import com.sportsbook.risk.reservation.ReservationKeys;
import com.sportsbook.risk.reservation.RiskReservationProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Deterministic key and argument order consumed by {@code risk-snapshot.lua}. */
public record RiskSnapshotScriptRequest(List<String> keys, List<String> arguments) {
  public RiskSnapshotScriptRequest {
    keys = List.copyOf(Objects.requireNonNull(keys, "keys"));
    arguments = List.copyOf(Objects.requireNonNull(arguments, "arguments"));
  }

  public static RiskSnapshotScriptRequest from(
      PatternContext context,
      RiskPatternProperties patterns,
      RiskReservationProperties reservations) {
    Objects.requireNonNull(context, "context");
    Objects.requireNonNull(patterns, "patterns");
    Objects.requireNonNull(reservations, "reservations");
    UserId userId = context.userId();
    Currency currency = context.stake().currency();
    List<String> keys = new ArrayList<>();
    add(keys, LimitKeys.monetary(userId, LimitType.STAKE_DAILY, currency));
    add(keys, LimitKeys.monetary(userId, LimitType.STAKE_WEEKLY, currency));
    add(keys, LimitKeys.monetary(userId, LimitType.STAKE_MONTHLY, currency));
    add(keys, LimitKeys.selections(userId));
    keys.add(LimitOverrideKeys.user(userId));
    keys.add(ReservationKeys.activeBets(userId));
    add(keys, ReservationKeys.activeStakes(userId, currency));
    add(keys, ReservationKeys.activeSelections(userId));
    keys.add(HistoryKeys.bets(userId));
    keys.add(HistoryKeys.stakes(userId, currency));
    keys.add(ReservationKeys.ACTIVE_COUNT);
    context.selections().stream()
        .map(selection -> HistoryKeys.selection(userId, selection))
        .forEach(keys::add);
    context.selections().stream()
        .map(selection -> ReservationKeys.activeSelection(userId, selection))
        .forEach(keys::add);

    List<String> arguments = new ArrayList<>();
    arguments.add(Long.toString(context.evaluatedAt().toEpochMilli()));
    arguments.add(Long.toString(reservations.retention().toMillis()));
    arguments.add(Long.toString(LimitType.STAKE_DAILY.window().toMillis()));
    arguments.add(Long.toString(LimitType.STAKE_WEEKLY.window().toMillis()));
    arguments.add(Long.toString(LimitType.STAKE_MONTHLY.window().toMillis()));
    arguments.add(Long.toString(LimitType.SELECTIONS_PER_MINUTE.window().toMillis()));
    arguments.add(enabled(patterns.rapidBetting().enabled()));
    arguments.add(Long.toString(patterns.rapidBetting().window().toMillis()));
    arguments.add(enabled(patterns.suddenStake().enabled()));
    arguments.add(Integer.toString(patterns.suddenStake().lookbackBets()));
    arguments.add(enabled(patterns.repeatedSelection().enabled()));
    arguments.add(Long.toString(patterns.repeatedSelection().window().toMillis()));
    arguments.add(userId.value().toString());
    arguments.add(currency.name());
    arguments.add(Integer.toString(context.selections().size()));
    context.selections().stream()
        .map(SelectionId::value)
        .map(Object::toString)
        .forEach(arguments::add);
    return new RiskSnapshotScriptRequest(keys, arguments);
  }

  private static void add(List<String> keys, LimitKeys.Keys pair) {
    keys.add(pair.entries());
    keys.add(pair.sum());
  }

  private static String enabled(boolean value) {
    return value ? "1" : "0";
  }
}
