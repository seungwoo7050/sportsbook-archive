package com.sportsbook.risk.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.protocol.value.BetId;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.SelectionId;
import com.sportsbook.protocol.value.UserId;
import com.sportsbook.risk.pattern.RiskHistoryProperties;
import com.sportsbook.risk.policy.RiskLimitProperties;
import com.sportsbook.risk.policy.RiskPatternProperties;
import com.sportsbook.risk.service.RiskCheckCommand;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReservationScriptRequestTest {
  @Test
  void composesImmutableKeysAndArguments() {
    RiskCheckCommand command =
        new RiskCheckCommand(
            UserId.of(new UUID(0, 1)),
            BetId.of(new UUID(0, 2)),
            Money.krw(10),
            List.of(SelectionId.of(new UUID(0, 3))),
            Instant.EPOCH);
    RiskLimitProperties limits = new RiskLimitProperties(null, null, null, null, 0);
    RiskPatternProperties patterns = new RiskPatternProperties(null, null, null);
    RiskReservationProperties reservations = new RiskReservationProperties(null, null);
    RiskHistoryProperties history = new RiskHistoryProperties(null, 0);

    ReservationScriptRequest request =
        ReservationScriptRequest.from(command, limits, patterns, reservations, history);

    assertThat(request.keys()).isEqualTo(ReservationScriptKeys.from(command));
    assertThat(request.arguments())
        .isEqualTo(
            ReservationScriptArguments.from(command, limits, patterns, reservations, history));
    assertThatThrownBy(() -> request.keys().add("extra"))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> new ReservationScriptRequest(new ArrayList<>(), null))
        .isInstanceOf(NullPointerException.class);
  }
}
