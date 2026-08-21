package com.sportsbook.risk.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.value.Currency;
import org.junit.jupiter.api.Test;

class ReservationCommitFootprintScriptTest extends ReservationScriptTestSupport {
  @Test
  void removesTheCompleteActiveFootprintOnCommit() {
    var first = selection(1_100);
    var second = selection(1_101);
    var command = command(1_100, 40, Currency.KRW, first, second);
    ReservationDecision reserved = reserve(command);

    assertThat(commit(command.betId(), reserved.token(), NOW.plusMillis(1)))
        .isEqualTo(ReservationTransition.APPLIED);

    assertThat(redis.hasKey(ReservationKeys.activeBets(USER))).isFalse();
    assertThat(redis.hasKey(ReservationKeys.activeStakes(USER, Currency.KRW).entries())).isFalse();
    assertThat(redis.hasKey(ReservationKeys.activeStakes(USER, Currency.KRW).sum())).isFalse();
    assertThat(redis.hasKey(ReservationKeys.activeSelections(USER).entries())).isFalse();
    assertThat(redis.hasKey(ReservationKeys.activeSelections(USER).sum())).isFalse();
    assertThat(redis.hasKey(ReservationKeys.activeSelection(USER, first))).isFalse();
    assertThat(redis.hasKey(ReservationKeys.activeSelection(USER, second))).isFalse();
    assertThat(redis.hasKey(ReservationKeys.ACTIVE_COUNT)).isFalse();
  }
}
