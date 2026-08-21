package com.sportsbook.risk.reservation;

import com.sportsbook.risk.counter.LimitType;
import com.sportsbook.risk.pattern.RiskHistoryProperties;
import com.sportsbook.risk.policy.RiskPatternProperties;
import com.sportsbook.risk.service.RiskCheckCommand;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** Typed input for an atomic first-seen accepted-bet projection. */
public record AcceptedProjectionRequest(List<String> keys, List<String> arguments) {
  public AcceptedProjectionRequest {
    keys = List.copyOf(Objects.requireNonNull(keys, "keys"));
    arguments = List.copyOf(Objects.requireNonNull(arguments, "arguments"));
  }

  public static AcceptedProjectionRequest from(
      RiskCheckCommand command,
      String fingerprint,
      RiskReservationProperties reservations,
      RiskPatternProperties patterns,
      RiskHistoryProperties history) {
    Objects.requireNonNull(command, "command");
    if (fingerprint == null || !fingerprint.matches("[0-9a-f]{64}")) {
      throw new IllegalArgumentException("accepted fingerprint must be lowercase SHA-256 hex");
    }
    String selections =
        command.selectionIds().stream()
            .map(selection -> selection.value().toString())
            .collect(Collectors.joining(","));
    return new AcceptedProjectionRequest(
        List.of(
            ReservationKeys.lifecycle(command.betId()),
            ReservationKeys.acceptedFingerprint(command.betId())),
        List.of(
            "1",
            Long.toString(command.now().toEpochMilli()),
            Long.toString(reservations.retention().toMillis()),
            fingerprint,
            command.userId().value().toString(),
            command.betId().value().toString(),
            Long.toString(command.stake().amount()),
            command.stake().currency().name(),
            Integer.toString(command.selectionIds().size()),
            selections,
            Long.toString(LimitType.STAKE_DAILY.window().toMillis()),
            Long.toString(LimitType.STAKE_WEEKLY.window().toMillis()),
            Long.toString(LimitType.STAKE_MONTHLY.window().toMillis()),
            Long.toString(LimitType.SELECTIONS_PER_MINUTE.window().toMillis()),
            Long.toString(patterns.rapidBetting().window().toMillis()),
            Long.toString(patterns.repeatedSelection().window().toMillis()),
            Long.toString(history.idleRetention().toMillis()),
            Integer.toString(history.maxStakeSamples())));
  }
}
