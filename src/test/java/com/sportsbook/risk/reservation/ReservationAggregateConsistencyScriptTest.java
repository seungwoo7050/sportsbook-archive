package com.sportsbook.risk.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.protocol.value.BetId;
import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.risk.service.RiskCheckCommand;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReservationAggregateConsistencyScriptTest extends ReservationScriptTestSupport {
  @Test
  void rejectsOverreportedStakeAggregateBeforeCleanup() {
    assertOverstatementAborts(
        ReservationKeys.activeStakes(USER, Currency.KRW).sum(),
        "41",
        "corrupt active stake aggregate");
  }

  @Test
  void rejectsOverreportedSelectionAggregateBeforeCleanup() {
    assertOverstatementAborts(
        ReservationKeys.activeSelections(USER).sum(), "2", "corrupt active selection aggregate");
  }

  private void assertOverstatementAborts(String key, String tampered, String error) {
    RiskCheckCommand expired = command(800, 40, Currency.KRW, selection(800));
    assertThat(reserve(expired).approved()).isTrue();
    redis.opsForValue().set(key, tampered);

    RiskCheckCommand next =
        new RiskCheckCommand(
            USER,
            BetId.of(new UUID(0, 801)),
            new Money(10, Currency.KRW),
            List.of(selection(801)),
            NOW.plus(RiskReservationProperties.DEFAULT_LEASE));
    assertThatThrownBy(() -> execute(request(next))).hasStackTraceContaining(error);

    assertThat(
            redis
                .<String, String>opsForHash()
                .get(ReservationKeys.lifecycle(expired.betId()), "state"))
        .isEqualTo("RESERVED");
    assertThat(redis.hasKey(ReservationKeys.lifecycle(next.betId()))).isFalse();
    assertThat(redis.opsForValue().get(key)).isEqualTo(tampered);
    assertThat(redis.opsForZSet().range(ReservationKeys.activeBets(USER), 0, -1))
        .containsExactly(expired.betId().value().toString());
    assertThat(redis.opsForValue().get(ReservationKeys.ACTIVE_COUNT)).isEqualTo("1");
  }
}
