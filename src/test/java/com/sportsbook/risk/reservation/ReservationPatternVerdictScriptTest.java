package com.sportsbook.risk.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.value.Currency;
import com.sportsbook.risk.policy.PatternAction;
import com.sportsbook.risk.policy.RapidBettingPolicy;
import com.sportsbook.risk.policy.RepeatedSelectionPolicy;
import com.sportsbook.risk.policy.RiskPatternProperties;
import com.sportsbook.risk.policy.SuddenStakePolicy;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class ReservationPatternVerdictScriptTest extends ReservationScriptTestSupport {
  @Test
  void persistsStableRuleOrderAndFirstBlockingVerdict() {
    reserve(command(500, 10, Currency.KRW));
    reserve(command(501, 10, Currency.KRW));
    RiskPatternProperties patterns =
        new RiskPatternProperties(
            new RapidBettingPolicy(true, Duration.ofMinutes(1), 3, PatternAction.SUSPECT),
            new SuddenStakePolicy(true, 2, 2, PatternAction.BLOCK),
            new RepeatedSelectionPolicy(true, Duration.ofDays(1), 2, PatternAction.REVIEW));
    var request = request(command(502, 30, Currency.KRW), limits(1_000), patterns);

    ReservationDecision first = execute(request).decision();
    ReservationDecision replay = execute(request).decision();

    assertThat(first.rejection()).isEqualTo("SUDDEN_STAKE_INCREASE");
    assertThat(first.patterns())
        .extracting(match -> match.rule())
        .containsExactly("RAPID_BETTING", "SUDDEN_STAKE_INCREASE", "REPEATED_SAME_SELECTION");
    assertThat(replay.replayed()).isTrue();
    assertThat(replay.rejection()).isEqualTo(first.rejection());
    assertThat(replay.patterns()).isEqualTo(first.patterns());
  }
}
