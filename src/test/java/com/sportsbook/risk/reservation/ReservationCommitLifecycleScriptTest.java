package com.sportsbook.risk.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.value.Currency;
import org.junit.jupiter.api.Test;

class ReservationCommitLifecycleScriptTest extends ReservationScriptTestSupport {
  @Test
  void requiresTheOpaqueReservationTokenAndReplaysCommit() {
    var command = command(1_000, 10, Currency.KRW);
    ReservationDecision reserved = reserve(command);

    assertThat(commit(command.betId(), "0".repeat(64), NOW.plusMillis(1)))
        .isEqualTo(ReservationTransition.CONFLICT);
    assertThat(commit(command.betId(), reserved.token(), NOW.plusMillis(1)))
        .isEqualTo(ReservationTransition.APPLIED);
    assertThat(commit(command.betId(), reserved.token(), NOW.plusMillis(2)))
        .isEqualTo(ReservationTransition.REPLAYED);
    assertThat(
            redis
                .<String, String>opsForHash()
                .get(ReservationKeys.lifecycle(command.betId()), "state"))
        .isEqualTo("COMMITTED");
  }

  @Test
  void tombstonesExpiredReservations() {
    var command = command(1_001, 10, Currency.KRW);
    ReservationDecision reserved = reserve(command);

    assertThat(
            commit(
                command.betId(),
                reserved.token(),
                NOW.plus(RiskReservationProperties.DEFAULT_LEASE)))
        .isEqualTo(ReservationTransition.EXPIRED);
    assertThat(
            redis
                .<String, String>opsForHash()
                .get(ReservationKeys.lifecycle(command.betId()), "state"))
        .isEqualTo("EXPIRED");
  }
}
