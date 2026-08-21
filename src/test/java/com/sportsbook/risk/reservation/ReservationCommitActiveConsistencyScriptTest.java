package com.sportsbook.risk.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.protocol.value.Currency;
import org.junit.jupiter.api.Test;

class ReservationCommitActiveConsistencyScriptTest extends ReservationScriptTestSupport {
  @Test
  void rejectsOverreportedActiveStakeBeforeCommitMutation() {
    var command = command(1_400, 10, Currency.KRW);
    ReservationDecision reserved = reserve(command);
    var active = ReservationKeys.activeStakes(USER, Currency.KRW);
    redis.opsForValue().set(active.sum(), "11");

    assertThatThrownBy(() -> commit(command.betId(), reserved.token(), NOW.plusMillis(1)))
        .hasStackTraceContaining("inconsistent active stake aggregate");

    assertThat(
            redis
                .<String, String>opsForHash()
                .get(ReservationKeys.lifecycle(command.betId()), "state"))
        .isEqualTo("RESERVED");
    assertThat(redis.opsForValue().get(active.sum())).isEqualTo("11");
    assertThat(redis.opsForZSet().range(active.entries(), 0, -1))
        .containsExactly(command.betId().value() + "|10");
  }
}
