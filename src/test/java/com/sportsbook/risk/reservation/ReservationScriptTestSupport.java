package com.sportsbook.risk.reservation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportsbook.protocol.value.BetId;
import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.SelectionId;
import com.sportsbook.protocol.value.UserId;
import com.sportsbook.risk.counter.RedisLuaScriptLoader;
import com.sportsbook.risk.pattern.RiskHistoryProperties;
import com.sportsbook.risk.policy.RiskLimitProperties;
import com.sportsbook.risk.policy.RiskPatternProperties;
import com.sportsbook.risk.service.RiskCheckCommand;
import com.sportsbook.risk.support.RedisTestSupport;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.redis.core.script.RedisScript;

abstract class ReservationScriptTestSupport extends RedisTestSupport {
  protected static final UserId USER = UserId.of(new UUID(0, 1));
  protected static final SelectionId SELECTION = SelectionId.of(new UUID(0, 2));
  protected static final Instant NOW = Instant.ofEpochMilli(2_000_000);
  private static final RedisScript<String> SCRIPT =
      RedisLuaScriptLoader.stringScript("risk-reserve.lua");
  private static final RedisScript<String> COMMIT =
      RedisLuaScriptLoader.stringScript("risk-commit.lua");
  private static final RedisScript<String> RELEASE =
      RedisLuaScriptLoader.stringScript("risk-release.lua");

  protected ReservationWireMapper.Decoded execute(ReservationScriptRequest request) {
    String raw = redis.execute(SCRIPT, request.keys(), request.arguments().toArray());
    return new ReservationWireMapper(new ObjectMapper()).map(raw);
  }

  protected ReservationDecision reserve(RiskCheckCommand command) {
    return execute(request(command)).decision();
  }

  protected ReservationTransition commit(BetId betId, String token, Instant now) {
    ReservationTransitionRequest request =
        ReservationTransitionRequest.commit(
            betId,
            token,
            now,
            new RiskReservationProperties(null, null),
            new RiskPatternProperties(null, null, null),
            new RiskHistoryProperties(null, 0));
    return ReservationTransition.valueOf(
        redis.execute(COMMIT, request.keys(), request.arguments().toArray()));
  }

  protected ReservationTransition release(BetId betId, Instant now) {
    ReservationTransitionRequest request =
        ReservationTransitionRequest.release(betId, now, new RiskReservationProperties(null, null));
    return ReservationTransition.valueOf(
        redis.execute(RELEASE, request.keys(), request.arguments().toArray()));
  }

  protected ReservationScriptRequest request(RiskCheckCommand command) {
    return request(
        command,
        new RiskLimitProperties(null, null, null, null, 0),
        new RiskPatternProperties(null, null, null));
  }

  protected ReservationScriptRequest request(
      RiskCheckCommand command, RiskLimitProperties limits, RiskPatternProperties patterns) {
    return ReservationScriptRequest.from(
        command,
        limits,
        patterns,
        new RiskReservationProperties(null, null),
        new RiskHistoryProperties(null, 0));
  }

  protected static RiskLimitProperties limits(long amount) {
    Map<Currency, Long> values = Map.of(Currency.KRW, amount, Currency.USD, amount);
    return new RiskLimitProperties(values, values, values, values, 30);
  }

  protected static RiskCheckCommand command(long bet, long amount, Currency currency) {
    return command(bet, amount, currency, SELECTION);
  }

  protected static RiskCheckCommand command(
      long bet, long amount, Currency currency, SelectionId... selections) {
    return new RiskCheckCommand(
        USER, BetId.of(new UUID(0, bet)), new Money(amount, currency), List.of(selections), NOW);
  }

  protected static SelectionId selection(long value) {
    return SelectionId.of(new UUID(0, value));
  }
}
