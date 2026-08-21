package com.sportsbook.risk.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.value.Currency;
import com.sportsbook.risk.counter.LimitKeys;
import com.sportsbook.risk.counter.LimitType;
import org.junit.jupiter.api.Test;

class ReservationCommitCapacityScriptTest extends ReservationScriptTestSupport {
  @Test
  void movesReservedExposureIntoCommittedWindows() {
    var command = command(1_200, 40, Currency.USD, selection(1_200), selection(1_201));
    ReservationDecision reserved = reserve(command);

    assertThat(commit(command.betId(), reserved.token(), NOW.plusMillis(1)))
        .isEqualTo(ReservationTransition.APPLIED);

    assertMonetaryWindow(command, LimitType.STAKE_DAILY);
    assertMonetaryWindow(command, LimitType.STAKE_WEEKLY);
    assertMonetaryWindow(command, LimitType.STAKE_MONTHLY);
    LimitKeys.Keys selections = LimitKeys.selections(command.userId());
    assertThat(redis.opsForValue().get(selections.sum())).isEqualTo("2");
    assertThat(redis.opsForZSet().range(selections.entries(), 0, -1))
        .containsExactly(LimitKeys.member(command.betId(), 2));
  }

  private void assertMonetaryWindow(
      com.sportsbook.risk.service.RiskCheckCommand command, LimitType type) {
    LimitKeys.Keys keys = LimitKeys.monetary(command.userId(), type, command.stake().currency());
    assertThat(redis.opsForValue().get(keys.sum())).isEqualTo("40");
    assertThat(redis.opsForZSet().range(keys.entries(), 0, -1))
        .containsExactly(LimitKeys.member(command.betId(), 40));
  }
}
