package com.sportsbook.risk.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.value.Currency;
import com.sportsbook.risk.pattern.HistoryKeys;
import org.junit.jupiter.api.Test;

class ReservationCommitHistoryScriptTest extends ReservationScriptTestSupport {
  @Test
  void projectsCommittedFactsIntoBoundedPatternHistory() {
    var first = selection(1_300);
    var second = selection(1_301);
    var command = command(1_300, 40, Currency.USD, first, second);
    ReservationDecision reserved = reserve(command);

    assertThat(commit(command.betId(), reserved.token(), NOW.plusMillis(1)))
        .isEqualTo(ReservationTransition.APPLIED);

    assertThat(redis.opsForZSet().range(HistoryKeys.bets(USER), 0, -1))
        .containsExactly(HistoryKeys.betMember(command.betId()));
    assertThat(redis.opsForZSet().range(HistoryKeys.stakes(USER, Currency.USD), 0, -1))
        .containsExactly(HistoryKeys.stakeMember(command.betId(), 40));
    assertThat(redis.opsForZSet().range(HistoryKeys.selection(USER, first), 0, -1))
        .containsExactly(HistoryKeys.betMember(command.betId()));
    assertThat(redis.opsForZSet().range(HistoryKeys.selection(USER, second), 0, -1))
        .containsExactly(HistoryKeys.betMember(command.betId()));
  }
}
