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

class ReservationCorruptFootprintScriptTest extends ReservationScriptTestSupport {
  @Test
  void rejectsIncompleteExpiryCleanupWithoutMutation() {
    RiskCheckCommand expired = command(700, 40, Currency.KRW, selection(700));
    RiskCheckCommand next =
        new RiskCheckCommand(
            USER,
            BetId.of(new UUID(0, 701)),
            new Money(10, Currency.KRW),
            List.of(selection(701)),
            NOW.plus(RiskReservationProperties.DEFAULT_LEASE));
    assertThat(reserve(expired).approved()).isTrue();
    redis.delete(ReservationKeys.activeSelection(USER, selection(700)));

    assertThatThrownBy(() -> execute(request(next)))
        .hasStackTraceContaining("missing selection footprint");

    assertThat(
            redis
                .<String, String>opsForHash()
                .get(ReservationKeys.lifecycle(expired.betId()), "state"))
        .isEqualTo("RESERVED");
    assertThat(redis.hasKey(ReservationKeys.lifecycle(next.betId()))).isFalse();
    assertThat(redis.opsForZSet().range(ReservationKeys.activeBets(USER), 0, -1))
        .containsExactly(expired.betId().value().toString());
    assertThat(redis.opsForValue().get(ReservationKeys.activeStakes(USER, Currency.KRW).sum()))
        .isEqualTo("40");
    assertThat(redis.opsForValue().get(ReservationKeys.activeSelections(USER).sum()))
        .isEqualTo("1");
    assertThat(redis.opsForValue().get(ReservationKeys.ACTIVE_COUNT)).isEqualTo("1");
  }
}
