package com.sportsbook.risk.reservation;

import com.sportsbook.protocol.value.BetId;
import com.sportsbook.risk.counter.RedisLuaScriptLoader;
import com.sportsbook.risk.pattern.RiskHistoryProperties;
import com.sportsbook.risk.policy.RiskLimitProperties;
import com.sportsbook.risk.policy.RiskPatternProperties;
import com.sportsbook.risk.service.RiskCheckCommand;
import java.time.Instant;
import java.util.Objects;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/** Executes each reservation operation as one standalone-Redis script. */
@Component
public final class RedisRiskReservationStore implements RiskReservationStore {
  private static final RedisScript<String> RESERVE =
      RedisLuaScriptLoader.stringScript("risk-reserve.lua");
  private static final RedisScript<String> COMMIT =
      RedisLuaScriptLoader.stringScript("risk-commit.lua");
  private static final RedisScript<String> RELEASE =
      RedisLuaScriptLoader.stringScript("risk-release.lua");

  private final StringRedisTemplate redis;
  private final RiskLimitProperties limits;
  private final RiskPatternProperties patterns;
  private final RiskReservationProperties reservations;
  private final RiskHistoryProperties history;
  private final ReservationWireMapper mapper;

  public RedisRiskReservationStore(
      StringRedisTemplate redis,
      RiskLimitProperties limits,
      RiskPatternProperties patterns,
      RiskReservationProperties reservations,
      RiskHistoryProperties history,
      ReservationWireMapper mapper) {
    this.redis = Objects.requireNonNull(redis, "redis");
    this.limits = Objects.requireNonNull(limits, "limits");
    this.patterns = Objects.requireNonNull(patterns, "patterns");
    this.reservations = Objects.requireNonNull(reservations, "reservations");
    this.history = Objects.requireNonNull(history, "history");
    this.mapper = Objects.requireNonNull(mapper, "mapper");
  }

  @Override
  public ReservationDecision reserve(RiskCheckCommand command) {
    ReservationScriptRequest request =
        ReservationScriptRequest.from(command, limits, patterns, reservations, history);
    String raw = redis.execute(RESERVE, request.keys(), request.arguments().toArray());
    return mapper.map(raw).decision();
  }

  @Override
  public ReservationTransition commit(BetId betId, String token, Instant now) {
    ReservationTransitionRequest request =
        ReservationTransitionRequest.commit(betId, token, now, reservations, patterns, history);
    return executeTransition(COMMIT, request, "commit");
  }

  @Override
  public ReservationTransition release(BetId betId, Instant now) {
    ReservationTransitionRequest request =
        ReservationTransitionRequest.release(betId, now, reservations);
    return executeTransition(RELEASE, request, "release");
  }

  private ReservationTransition executeTransition(
      RedisScript<String> script, ReservationTransitionRequest request, String operation) {
    String raw = redis.execute(script, request.keys(), request.arguments().toArray());
    if (raw == null) {
      throw new IllegalStateException("Redis " + operation + " script returned no result");
    }
    try {
      return ReservationTransition.valueOf(raw);
    } catch (IllegalArgumentException failure) {
      throw new IllegalStateException(
          "Redis " + operation + " script returned unknown result", failure);
    }
  }
}
