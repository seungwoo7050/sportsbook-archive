package com.sportsbook.risk.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportsbook.protocol.value.BetId;
import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.SelectionId;
import com.sportsbook.protocol.value.UserId;
import com.sportsbook.risk.counter.LimitKeys;
import com.sportsbook.risk.counter.LimitType;
import com.sportsbook.risk.counter.RedisLuaScriptLoader;
import com.sportsbook.risk.pattern.RiskHistoryProperties;
import com.sportsbook.risk.policy.RiskLimitProperties;
import com.sportsbook.risk.policy.RiskPatternProperties;
import com.sportsbook.risk.service.RiskCheckCommand;
import com.sportsbook.risk.support.RedisTestSupport;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.script.RedisScript;

class AcceptedReservationBoundaryScriptTest extends RedisTestSupport {
  private static final RedisScript<String> PROJECT =
      RedisLuaScriptLoader.stringScript("risk-project-accepted.lua");
  private static final RedisScript<String> RESERVE =
      RedisLuaScriptLoader.stringScript("risk-reserve.lua");
  private static final UserId USER = UserId.of(new UUID(0, 1));
  private static final BetId BET = BetId.of(new UUID(0, 10));
  private static final RiskCheckCommand COMMAND =
      new RiskCheckCommand(
          USER,
          BET,
          Money.krw(50),
          List.of(SelectionId.of(new UUID(0, 20))),
          Instant.ofEpochMilli(2_000_000));

  @Test
  void acceptedProjectionPreventsReservationReadmission() {
    var reservations = new RiskReservationProperties(null, null);
    var patterns = new RiskPatternProperties(null, null, null);
    var history = new RiskHistoryProperties(null, 0);
    String fingerprint = ReservationFingerprint.of(COMMAND);
    AcceptedProjectionRequest projection =
        AcceptedProjectionRequest.from(COMMAND, fingerprint, reservations, patterns, history);
    ReservationScriptRequest admission =
        ReservationScriptRequest.from(
            COMMAND,
            new RiskLimitProperties(null, null, null, null, 0),
            patterns,
            reservations,
            history);

    assertThat(redis.execute(PROJECT, projection.keys(), projection.arguments().toArray()))
        .isEqualTo("APPLIED");
    ReservationDecision decision =
        new ReservationWireMapper(new ObjectMapper())
            .map(redis.execute(RESERVE, admission.keys(), admission.arguments().toArray()))
            .decision();

    assertThat(decision.status()).isEqualTo(ReservationDecision.Status.CONFLICT);
    assertThat(redis.hasKey(ReservationKeys.lifecycle(BET))).isFalse();
    assertThat(redis.hasKey(ReservationKeys.activeBets(USER))).isFalse();
    assertThat(
            redis
                .opsForValue()
                .get(LimitKeys.monetary(USER, LimitType.STAKE_DAILY, Currency.KRW).sum()))
        .isEqualTo("50");
  }
}
