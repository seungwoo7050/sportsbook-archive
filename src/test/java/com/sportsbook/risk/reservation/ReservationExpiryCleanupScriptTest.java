package com.sportsbook.risk.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.value.BetId;
import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.risk.service.RiskCheckCommand;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReservationExpiryCleanupScriptTest extends ReservationScriptTestSupport {
  @Test
  void removesEveryExpiredCapacityFootprintBeforeAdmission() {
    RiskCheckCommand expired = command(600, 40, Currency.KRW, selection(600));
    RiskCheckCommand next =
        new RiskCheckCommand(
            USER,
            BetId.of(new UUID(0, 601)),
            new Money(10, Currency.KRW),
            List.of(selection(601)),
            NOW.plus(RiskReservationProperties.DEFAULT_LEASE));

    assertThat(reserve(expired).approved()).isTrue();
    ReservationWireMapper.Decoded admitted = execute(request(next));

    assertThat(admitted.decision().approved()).isTrue();
    assertThat(admitted.expired()).isEqualTo(1);
    assertThat(
            redis
                .<String, String>opsForHash()
                .get(ReservationKeys.lifecycle(expired.betId()), "state"))
        .isEqualTo("EXPIRED");
    assertThat(redis.hasKey(ReservationKeys.activeSelection(USER, selection(600)))).isFalse();
    assertThat(redis.opsForZSet().range(ReservationKeys.activeBets(USER), 0, -1))
        .containsExactly(next.betId().value().toString());
    assertThat(redis.opsForValue().get(ReservationKeys.activeStakes(USER, Currency.KRW).sum()))
        .isEqualTo("10");
    assertThat(redis.opsForValue().get(ReservationKeys.activeSelections(USER).sum()))
        .isEqualTo("1");
    assertThat(redis.opsForValue().get(ReservationKeys.ACTIVE_COUNT)).isEqualTo("1");
  }
}
