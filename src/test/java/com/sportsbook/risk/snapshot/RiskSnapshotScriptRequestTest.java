package com.sportsbook.risk.snapshot;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.value.BetId;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.SelectionId;
import com.sportsbook.protocol.value.UserId;
import com.sportsbook.risk.pattern.PatternContext;
import com.sportsbook.risk.policy.PatternAction;
import com.sportsbook.risk.policy.RapidBettingPolicy;
import com.sportsbook.risk.policy.RepeatedSelectionPolicy;
import com.sportsbook.risk.policy.RiskPatternProperties;
import com.sportsbook.risk.policy.SuddenStakePolicy;
import com.sportsbook.risk.reservation.RiskReservationProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RiskSnapshotScriptRequestTest {
  private static final UserId USER = UserId.of(new UUID(0, 1));
  private static final SelectionId FIRST = SelectionId.of(new UUID(0, 3));
  private static final SelectionId SECOND = SelectionId.of(new UUID(0, 4));

  @Test
  void assemblesCurrencyNeutralAndCurrencyScopedInputsInStableOrder() {
    RiskPatternProperties patterns =
        new RiskPatternProperties(
            new RapidBettingPolicy(true, Duration.ofSeconds(45), 7, PatternAction.BLOCK),
            new SuddenStakePolicy(false, 5, 8, PatternAction.SUSPECT),
            new RepeatedSelectionPolicy(true, Duration.ofHours(2), 3, PatternAction.REVIEW));
    PatternContext context =
        new PatternContext(
            USER,
            BetId.of(new UUID(0, 2)),
            Money.usd(100),
            List.of(FIRST, SECOND),
            Instant.ofEpochMilli(1234));

    RiskSnapshotScriptRequest request =
        RiskSnapshotScriptRequest.from(
            context, patterns, new RiskReservationProperties(Duration.ofMinutes(2), null));

    assertThat(request.keys()).hasSize(21);
    assertThat(request.keys().get(0)).contains("stake-daily", "usd");
    assertThat(request.keys().get(6)).contains("selections-per-minute").doesNotContain("usd");
    assertThat(request.keys().get(8)).isEqualTo("risk:limit:override:{" + USER.value() + "}");
    assertThat(request.keys().subList(17, 19))
        .allMatch(key -> key.startsWith("risk:history:") && key.contains(":selection:"));
    assertThat(request.keys().subList(19, 21))
        .allMatch(key -> key.startsWith("risk:reservations:user:"));
    assertThat(request.arguments())
        .containsExactly(
            "1234",
            Long.toString(Duration.ofDays(32).toMillis()),
            "86400000",
            "604800000",
            "2592000000",
            "60000",
            "1",
            "45000",
            "0",
            "8",
            "1",
            "7200000",
            USER.value().toString(),
            "USD",
            "2",
            FIRST.value().toString(),
            SECOND.value().toString());
    assertThat(request.keys()).isUnmodifiable();
    assertThat(request.arguments()).isUnmodifiable();
  }
}
