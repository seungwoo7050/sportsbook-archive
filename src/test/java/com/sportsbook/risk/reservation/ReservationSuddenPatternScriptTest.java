package com.sportsbook.risk.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.value.Currency;
import com.sportsbook.risk.policy.PatternAction;
import com.sportsbook.risk.policy.RapidBettingPolicy;
import com.sportsbook.risk.policy.RepeatedSelectionPolicy;
import com.sportsbook.risk.policy.RiskPatternProperties;
import com.sportsbook.risk.policy.SuddenStakePolicy;
import org.junit.jupiter.api.Test;

class ReservationSuddenPatternScriptTest extends ReservationScriptTestSupport {
  @Test
  void evaluatesSuddenStakeAgainstSameCurrencyActiveFacts() {
    RiskPatternProperties patterns =
        new RiskPatternProperties(
            RapidBettingPolicy.defaults(),
            new SuddenStakePolicy(true, 3, 2, PatternAction.BLOCK),
            RepeatedSelectionPolicy.defaults());

    assertThat(
            execute(
                    request(
                        command(400, 10, Currency.KRW, selection(400)), limits(1_000), patterns))
                .decision()
                .approved())
        .isTrue();
    assertThat(
            execute(
                    request(
                        command(401, 10, Currency.KRW, selection(401)), limits(1_000), patterns))
                .decision()
                .approved())
        .isTrue();
    assertThat(
            execute(
                    request(
                        command(402, 30, Currency.KRW, selection(402)), limits(1_000), patterns))
                .decision()
                .rejection())
        .isEqualTo("SUDDEN_STAKE_INCREASE");
    assertThat(
            execute(
                    request(
                        command(403, 30, Currency.USD, selection(403)), limits(1_000), patterns))
                .decision()
                .approved())
        .isTrue();
  }
}
