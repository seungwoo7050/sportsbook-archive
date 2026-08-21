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

class ReservationRepeatedPatternScriptTest extends ReservationScriptTestSupport {
  @Test
  void sharesRepeatedSelectionCapacityAcrossCurrencies() {
    RiskPatternProperties patterns =
        new RiskPatternProperties(
            RapidBettingPolicy.defaults(),
            SuddenStakePolicy.defaults(),
            new RepeatedSelectionPolicy(true, Duration.ofDays(1), 2, PatternAction.BLOCK));

    ReservationDecision first =
        execute(request(command(300, 10, Currency.KRW), limits(1_000), patterns)).decision();
    ReservationDecision second =
        execute(request(command(301, 10, Currency.USD), limits(1_000), patterns)).decision();
    ReservationDecision blocked =
        execute(request(command(302, 10, Currency.KRW), limits(1_000), patterns)).decision();

    assertThat(first.approved()).isTrue();
    assertThat(second.approved()).isTrue();
    assertThat(blocked.rejection()).isEqualTo("REPEATED_SAME_SELECTION");
    assertThat(redis.opsForZSet().size(ReservationKeys.activeSelection(USER, SELECTION)))
        .isEqualTo(2);
    assertThat(redis.hasKey(ReservationKeys.lifecycle(command(302, 10, Currency.KRW).betId())))
        .isTrue();
  }
}
