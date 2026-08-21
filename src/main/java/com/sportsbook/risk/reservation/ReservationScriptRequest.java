package com.sportsbook.risk.reservation;

import com.sportsbook.risk.pattern.RiskHistoryProperties;
import com.sportsbook.risk.policy.RiskLimitProperties;
import com.sportsbook.risk.policy.RiskPatternProperties;
import com.sportsbook.risk.service.RiskCheckCommand;
import java.util.List;
import java.util.Objects;

/** Typed immutable invocation for one atomic reservation admission. */
public record ReservationScriptRequest(List<String> keys, List<String> arguments) {
  public ReservationScriptRequest {
    keys = List.copyOf(Objects.requireNonNull(keys, "keys"));
    arguments = List.copyOf(Objects.requireNonNull(arguments, "arguments"));
  }

  public static ReservationScriptRequest from(
      RiskCheckCommand command,
      RiskLimitProperties limits,
      RiskPatternProperties patterns,
      RiskReservationProperties reservations,
      RiskHistoryProperties history) {
    return new ReservationScriptRequest(
        ReservationScriptKeys.from(command),
        ReservationScriptArguments.from(command, limits, patterns, reservations, history));
  }
}
