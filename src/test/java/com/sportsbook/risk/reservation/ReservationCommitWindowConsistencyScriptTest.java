package com.sportsbook.risk.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.protocol.value.Currency;
import com.sportsbook.risk.counter.LimitKeys;
import com.sportsbook.risk.counter.LimitType;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReservationCommitWindowConsistencyScriptTest extends ReservationScriptTestSupport {
  @Test
  void preservesActiveCapacityWhenCommittedWindowIsCorrupt() {
    var command = command(1_500, 10, Currency.KRW);
    ReservationDecision reserved = reserve(command);
    LimitKeys.Keys daily = LimitKeys.monetary(USER, LimitType.STAKE_DAILY, Currency.KRW);
    redis
        .opsForZSet()
        .add(daily.entries(), new UUID(0, 1_599) + "|10", NOW.minusMillis(1).toEpochMilli());
    redis.opsForValue().set(daily.sum(), "11");

    assertThatThrownBy(() -> commit(command.betId(), reserved.token(), NOW.plusMillis(1)))
        .hasStackTraceContaining("inconsistent rolling sum");

    assertThat(
            redis
                .<String, String>opsForHash()
                .get(ReservationKeys.lifecycle(command.betId()), "state"))
        .isEqualTo("RESERVED");
    assertThat(redis.opsForValue().get(ReservationKeys.activeStakes(USER, Currency.KRW).sum()))
        .isEqualTo("10");
    assertThat(redis.opsForValue().get(daily.sum())).isEqualTo("11");
  }
}
