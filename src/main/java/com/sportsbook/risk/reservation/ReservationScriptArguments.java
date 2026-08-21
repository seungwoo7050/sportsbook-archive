package com.sportsbook.risk.reservation;

import com.sportsbook.protocol.value.SelectionId;
import com.sportsbook.risk.counter.LimitType;
import com.sportsbook.risk.pattern.RiskHistoryProperties;
import com.sportsbook.risk.policy.RiskLimitProperties;
import com.sportsbook.risk.policy.RiskPatternProperties;
import com.sportsbook.risk.service.RiskCheckCommand;
import java.util.ArrayList;
import java.util.List;

/** Canonical precision-safe argument order consumed by reservation admission. */
final class ReservationScriptArguments {
  private ReservationScriptArguments() {}

  static List<String> from(
      RiskCheckCommand command,
      RiskLimitProperties limits,
      RiskPatternProperties patterns,
      RiskReservationProperties reservations,
      RiskHistoryProperties history) {
    var currency = command.stake().currency();
    List<String> values = new ArrayList<>();
    values.add("1");
    values.add(Long.toString(command.now().toEpochMilli()));
    values.add(Long.toString(reservations.lease().toMillis()));
    values.add(Long.toString(reservations.retention().toMillis()));
    values.add(ReservationFingerprint.of(command));
    values.add(command.userId().value().toString());
    values.add(command.betId().value().toString());
    values.add(Long.toString(command.stake().amount()));
    values.add(currency.name());
    values.add(Integer.toString(command.selectionIds().size()));
    values.add(Long.toString(limits.singleBetMax(currency)));
    values.add(Long.toString(limits.limit(LimitType.STAKE_DAILY, currency)));
    values.add(Long.toString(limits.limit(LimitType.STAKE_WEEKLY, currency)));
    values.add(Long.toString(limits.limit(LimitType.STAKE_MONTHLY, currency)));
    values.add(Long.toString(limits.limit(LimitType.SELECTIONS_PER_MINUTE, currency)));
    values.add(Long.toString(LimitType.STAKE_DAILY.window().toMillis()));
    values.add(Long.toString(LimitType.STAKE_WEEKLY.window().toMillis()));
    values.add(Long.toString(LimitType.STAKE_MONTHLY.window().toMillis()));
    values.add(Long.toString(LimitType.SELECTIONS_PER_MINUTE.window().toMillis()));
    values.add(enabled(patterns.rapidBetting().enabled()));
    values.add(Long.toString(patterns.rapidBetting().window().toMillis()));
    values.add(Integer.toString(patterns.rapidBetting().maxBets()));
    values.add(patterns.rapidBetting().action().name());
    values.add(enabled(patterns.suddenStake().enabled()));
    values.add(Integer.toString(patterns.suddenStake().multiplier()));
    values.add(Integer.toString(patterns.suddenStake().lookbackBets()));
    values.add(patterns.suddenStake().action().name());
    values.add(enabled(patterns.repeatedSelection().enabled()));
    values.add(Long.toString(patterns.repeatedSelection().window().toMillis()));
    values.add(Integer.toString(patterns.repeatedSelection().maxCount()));
    values.add(patterns.repeatedSelection().action().name());
    values.add(Long.toString(history.idleRetention().toMillis()));
    values.add(Integer.toString(history.maxStakeSamples()));
    command.selectionIds().stream()
        .map(SelectionId::value)
        .map(Object::toString)
        .forEach(values::add);
    return List.copyOf(values);
  }

  private static String enabled(boolean value) {
    return value ? "1" : "0";
  }
}
