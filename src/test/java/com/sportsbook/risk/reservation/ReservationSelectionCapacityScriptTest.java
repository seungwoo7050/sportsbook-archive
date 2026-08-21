package com.sportsbook.risk.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.value.Currency;
import com.sportsbook.risk.limit.LimitOverrideField;
import com.sportsbook.risk.limit.LimitOverrideKeys;
import com.sportsbook.risk.policy.RiskLimitProperties;
import com.sportsbook.risk.policy.RiskPatternProperties;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReservationSelectionCapacityScriptTest extends ReservationScriptTestSupport {
  @Test
  void sharesSelectionCapacityAcrossCurrenciesAndHonorsNeutralOverrides() {
    Map<Currency, Long> monetary = Map.of(Currency.KRW, 1_000L, Currency.USD, 1_000L);
    RiskLimitProperties limits = new RiskLimitProperties(monetary, monetary, monetary, monetary, 2);
    RiskPatternProperties patterns = new RiskPatternProperties(null, null, null);

    assertThat(
            execute(
                    request(
                        command(40, 10, Currency.KRW, selection(40), selection(41)),
                        limits,
                        patterns))
                .decision()
                .approved())
        .isTrue();
    assertThat(
            execute(request(command(41, 10, Currency.USD), limits, patterns))
                .decision()
                .rejection())
        .isEqualTo("SELECTIONS_PER_MINUTE_LIMIT_EXCEEDED");

    redis
        .opsForHash()
        .put(LimitOverrideKeys.user(USER), LimitOverrideField.selections().redisField(), "3");
    assertThat(
            execute(request(command(42, 10, Currency.USD), limits, patterns)).decision().approved())
        .isTrue();
    assertThat(redis.opsForValue().get(ReservationKeys.activeSelections(USER).sum()))
        .isEqualTo("3");
  }
}
