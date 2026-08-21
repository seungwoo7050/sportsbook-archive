package com.sportsbook.risk.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.value.BetId;
import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.SelectionId;
import com.sportsbook.protocol.value.UserId;
import com.sportsbook.risk.pattern.RiskHistoryProperties;
import com.sportsbook.risk.policy.PatternAction;
import com.sportsbook.risk.policy.RapidBettingPolicy;
import com.sportsbook.risk.policy.RepeatedSelectionPolicy;
import com.sportsbook.risk.policy.RiskLimitProperties;
import com.sportsbook.risk.policy.RiskPatternProperties;
import com.sportsbook.risk.policy.SuddenStakePolicy;
import com.sportsbook.risk.service.RiskCheckCommand;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReservationScriptArgumentsTest {
  @Test
  void ordersPoliciesAndTypedCandidateFacts() {
    SelectionId first = SelectionId.of(new UUID(0, 3));
    SelectionId second = SelectionId.of(new UUID(0, 4));
    RiskCheckCommand command =
        new RiskCheckCommand(
            UserId.of(new UUID(0, 1)),
            BetId.of(new UUID(0, 2)),
            new Money(70, Currency.USD),
            List.of(first, second),
            Instant.ofEpochMilli(1234));
    Map<Currency, Long> limits = Map.of(Currency.KRW, 90L, Currency.USD, 80L);
    RiskPatternProperties patterns =
        new RiskPatternProperties(
            new RapidBettingPolicy(true, Duration.ofSeconds(5), 6, PatternAction.BLOCK),
            new SuddenStakePolicy(true, 7, 8, PatternAction.REVIEW),
            new RepeatedSelectionPolicy(true, Duration.ofSeconds(9), 10, PatternAction.SUSPECT));

    List<String> values =
        ReservationScriptArguments.from(
            command,
            new RiskLimitProperties(limits, limits, limits, limits, 11),
            patterns,
            new RiskReservationProperties(Duration.ofMinutes(3), Duration.ofDays(33)),
            new RiskHistoryProperties(Duration.ofDays(8), 12));

    assertThat(values.subList(0, 11))
        .containsExactly(
            "1",
            "1234",
            "180000",
            "2851200000",
            ReservationFingerprint.of(command),
            command.userId().value().toString(),
            command.betId().value().toString(),
            "70",
            "USD",
            "2",
            "80");
    assertThat(values.subList(11, 19))
        .containsExactly("80", "80", "80", "11", "86400000", "604800000", "2592000000", "60000");
    assertThat(values.subList(19, 33))
        .containsExactly(
            "1",
            "5000",
            "6",
            "BLOCK",
            "1",
            "7",
            "8",
            "REVIEW",
            "1",
            "9000",
            "10",
            "SUSPECT",
            "691200000",
            "12");
    assertThat(values.subList(33, 35))
        .containsExactly(first.value().toString(), second.value().toString());
  }
}
