package com.sportsbook.risk.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.protocol.value.Currency;
import org.junit.jupiter.api.Test;

class ReservationReleaseConsistencyScriptTest extends ReservationScriptTestSupport {
  @Test
  void rejectsOverreportedSelectionsBeforeReleaseMutation() {
    var command = command(1_700, 10, Currency.KRW);
    assertThat(reserve(command).approved()).isTrue();
    redis.opsForValue().set(ReservationKeys.activeSelections(USER).sum(), "2");

    assertThatThrownBy(() -> release(command.betId(), NOW.plusMillis(1)))
        .hasStackTraceContaining("inconsistent active selection aggregate");

    assertThat(
            redis
                .<String, String>opsForHash()
                .get(ReservationKeys.lifecycle(command.betId()), "state"))
        .isEqualTo("RESERVED");
    assertThat(redis.opsForValue().get(ReservationKeys.activeSelections(USER).sum()))
        .isEqualTo("2");
    assertThat(redis.opsForZSet().size(ReservationKeys.activeBets(USER))).isEqualTo(1);
  }
}
