package com.sportsbook.risk.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.value.Currency;
import org.junit.jupiter.api.Test;

class ReservationReleaseLifecycleScriptTest extends ReservationScriptTestSupport {
  @Test
  void releasesCapacityAndReplaysTheTerminalResult() {
    var command = command(1_600, 20, Currency.KRW, selection(1_600), selection(1_601));
    reserve(command);

    assertThat(release(command.betId(), NOW.plusMillis(1)))
        .isEqualTo(ReservationTransition.APPLIED);
    assertThat(release(command.betId(), NOW.plusMillis(2)))
        .isEqualTo(ReservationTransition.REPLAYED);
    assertThat(
            redis
                .<String, String>opsForHash()
                .get(ReservationKeys.lifecycle(command.betId()), "state"))
        .isEqualTo("RELEASED");
    assertThat(redis.hasKey(ReservationKeys.activeBets(USER))).isFalse();
    assertThat(redis.hasKey(ReservationKeys.activeStakes(USER, Currency.KRW).sum())).isFalse();
    assertThat(redis.hasKey(ReservationKeys.activeSelections(USER).sum())).isFalse();
    assertThat(redis.hasKey(ReservationKeys.ACTIVE_COUNT)).isFalse();
  }

  @Test
  void preventsCommittedReservationsFromBeingReleased() {
    var command = command(1_601, 10, Currency.KRW);
    ReservationDecision reserved = reserve(command);
    commit(command.betId(), reserved.token(), NOW.plusMillis(1));

    assertThat(release(command.betId(), NOW.plusMillis(2)))
        .isEqualTo(ReservationTransition.CONFLICT);
  }
}
