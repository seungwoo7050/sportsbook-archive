package com.sportsbook.risk.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.protocol.value.Currency;
import com.sportsbook.risk.policy.RiskPatternProperties;
import com.sportsbook.risk.service.RiskCheckCommand;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class RiskReserveScriptTest extends ReservationScriptTestSupport {
  @Test
  void approvesAValidCandidateAndPersistsItsToken() {
    RiskCheckCommand command = command(10, 100, Currency.KRW);

    ReservationDecision decision = reserve(command);

    assertThat(decision.approved()).isTrue();
    assertThat(decision.state()).isEqualTo(ReservationState.RESERVED);
    assertThat(decision.token()).isEqualTo(ReservationFingerprint.of(command));
    assertThat(redis.opsForHash().get(ReservationKeys.lifecycle(command.betId()), "fingerprint"))
        .isEqualTo(decision.token());
    assertThat(redis.opsForValue().get(ReservationKeys.activeStakes(USER, Currency.KRW).sum()))
        .isEqualTo("100");
    assertThat(redis.opsForValue().get(ReservationKeys.ACTIVE_COUNT)).isEqualTo("1");
  }

  @Test
  void rejectsSingleBetLimitWithoutActiveCapacity() {
    RiskCheckCommand command = command(11, 60, Currency.KRW);

    ReservationDecision decision =
        execute(request(command, limits(50), new RiskPatternProperties(null, null, null)))
            .decision();

    assertThat(decision.approved()).isFalse();
    assertThat(decision.rejection()).isEqualTo("SINGLE_BET_MAX_EXCEEDED");
    assertThat(redis.opsForHash().get(ReservationKeys.lifecycle(command.betId()), "state"))
        .isEqualTo("REJECTED");
    assertThat(redis.hasKey(ReservationKeys.activeBets(USER))).isFalse();
    assertThat(redis.opsForValue().get(ReservationKeys.ACTIVE_COUNT)).isNull();
  }

  @Test
  void rejectsMalformedScriptArgumentsBeforeMutation() {
    RiskCheckCommand command = command(12, 10, Currency.KRW);
    ReservationScriptRequest valid = request(command);
    ArrayList<String> arguments = new ArrayList<>(valid.arguments());
    arguments.set(0, "2");

    assertThatThrownBy(() -> execute(new ReservationScriptRequest(valid.keys(), arguments)))
        .hasStackTraceContaining("invalid reservation request");
    assertThat(redis.hasKey(ReservationKeys.lifecycle(command.betId()))).isFalse();
  }
}
