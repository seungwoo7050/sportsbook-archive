package com.sportsbook.risk.reservation;

import com.sportsbook.risk.counter.LimitKeys;
import com.sportsbook.risk.counter.LimitType;
import com.sportsbook.risk.limit.LimitOverrideKeys;
import com.sportsbook.risk.pattern.HistoryKeys;
import com.sportsbook.risk.service.RiskCheckCommand;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Canonical key order consumed by the reservation admission script. */
final class ReservationScriptKeys {
  private ReservationScriptKeys() {}

  static List<String> from(RiskCheckCommand command) {
    Objects.requireNonNull(command, "command");
    var userId = command.userId();
    var currency = command.stake().currency();
    List<String> keys = new ArrayList<>();
    keys.add(ReservationKeys.lifecycle(command.betId()));
    keys.add(ReservationKeys.activeBets(userId));
    add(keys, ReservationKeys.activeStakes(userId, currency));
    add(keys, ReservationKeys.activeSelections(userId));
    keys.add(LimitOverrideKeys.user(userId));
    add(keys, LimitKeys.monetary(userId, LimitType.STAKE_DAILY, currency));
    add(keys, LimitKeys.monetary(userId, LimitType.STAKE_WEEKLY, currency));
    add(keys, LimitKeys.monetary(userId, LimitType.STAKE_MONTHLY, currency));
    add(keys, LimitKeys.selections(userId));
    keys.add(HistoryKeys.bets(userId));
    keys.add(HistoryKeys.stakes(userId, currency));
    keys.add(ReservationKeys.ACTIVE_COUNT);
    command.selectionIds().stream()
        .map(selection -> HistoryKeys.selection(userId, selection))
        .forEach(keys::add);
    command.selectionIds().stream()
        .map(selection -> ReservationKeys.activeSelection(userId, selection))
        .forEach(keys::add);
    return List.copyOf(keys);
  }

  private static void add(List<String> keys, LimitKeys.Keys pair) {
    keys.add(pair.entries());
    keys.add(pair.sum());
  }
}
