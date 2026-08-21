package com.sportsbook.risk.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.value.Currency;
import com.sportsbook.risk.policy.RiskPatternProperties;
import com.sportsbook.risk.service.RiskCheckCommand;
import org.junit.jupiter.api.Test;

class ReservationReplayScriptTest extends ReservationScriptTestSupport {
  @Test
  void replaysTheSameReservedIdentityAndRejectsChangedPayloads() {
    RiskCheckCommand original = command(20, 100, Currency.KRW);

    ReservationDecision first = reserve(original);
    ReservationDecision replay = reserve(original);
    ReservationDecision conflict = reserve(command(20, 101, Currency.KRW));

    assertThat(first.replayed()).isFalse();
    assertThat(replay.replayed()).isTrue();
    assertThat(replay.token()).isEqualTo(first.token());
    assertThat(replay.state()).isEqualTo(ReservationState.RESERVED);
    assertThat(conflict.status()).isEqualTo(ReservationDecision.Status.CONFLICT);
    assertThat(redis.opsForValue().get(ReservationKeys.activeStakes(USER, Currency.KRW).sum()))
        .isEqualTo("100");
  }

  @Test
  void replaysStoredBusinessRejections() {
    RiskCheckCommand command = command(21, 60, Currency.KRW);
    ReservationScriptRequest request =
        request(command, limits(50), new RiskPatternProperties(null, null, null));

    ReservationDecision first = execute(request).decision();
    ReservationDecision replay = execute(request).decision();

    assertThat(first.replayed()).isFalse();
    assertThat(replay.replayed()).isTrue();
    assertThat(replay.rejection()).isEqualTo("SINGLE_BET_MAX_EXCEEDED");
    assertThat(redis.hasKey(ReservationKeys.activeBets(USER))).isFalse();
  }
}
