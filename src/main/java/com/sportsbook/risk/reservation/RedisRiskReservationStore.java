package com.sportsbook.risk.reservation;

import com.sportsbook.protocol.value.BetId;
import com.sportsbook.risk.counter.RedisLuaScriptLoader;
import com.sportsbook.risk.pattern.RiskHistoryProperties;
import com.sportsbook.risk.policy.RiskLimitProperties;
import com.sportsbook.risk.policy.RiskPatternProperties;
import com.sportsbook.risk.service.RiskCheckCommand;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
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
  private static final RedisScript<String> PROJECT_ACCEPTED =
      RedisLuaScriptLoader.stringScript("risk-project-accepted.lua");
  private static final RedisScript<String> RELEASE =
      RedisLuaScriptLoader.stringScript("risk-release.lua");

  private final StringRedisTemplate redis;
  private final RiskLimitProperties limits;
  private final RiskPatternProperties patterns;
  private final RiskReservationProperties reservations;
  private final RiskHistoryProperties history;
  private final ReservationWireMapper mapper;
  private final Timer reserveLatency;
  private final Timer commitLatency;
  private final Timer acceptedProjectionLatency;
  private final Timer releaseLatency;

  @Autowired
  public RedisRiskReservationStore(
      StringRedisTemplate redis,
      RiskLimitProperties limits,
      RiskPatternProperties patterns,
      RiskReservationProperties reservations,
      RiskHistoryProperties history,
      ReservationWireMapper mapper,
      MeterRegistry meters) {
    this.redis = Objects.requireNonNull(redis, "redis");
    this.limits = Objects.requireNonNull(limits, "limits");
    this.patterns = Objects.requireNonNull(patterns, "patterns");
    this.reservations = Objects.requireNonNull(reservations, "reservations");
    this.history = Objects.requireNonNull(history, "history");
    this.mapper = Objects.requireNonNull(mapper, "mapper");
    this.reserveLatency = timer(meters, "reserve");
    this.commitLatency = timer(meters, "commit");
    this.acceptedProjectionLatency = timer(meters, "project-accepted");
    this.releaseLatency = timer(meters, "release");
  }

  RedisRiskReservationStore(
      StringRedisTemplate redis,
      RiskLimitProperties limits,
      RiskPatternProperties patterns,
      RiskReservationProperties reservations,
      RiskHistoryProperties history,
      ReservationWireMapper mapper) {
    this(redis, limits, patterns, reservations, history, mapper, Metrics.globalRegistry);
  }

  @Override
  public ReservationDecision reserve(RiskCheckCommand command) {
    ReservationScriptRequest request =
        ReservationScriptRequest.from(command, limits, patterns, reservations, history);
    return reserveLatency
        .record(
            () -> mapper.map(redis.execute(RESERVE, request.keys(), request.arguments().toArray())))
        .decision();
  }

  @Override
  public ReservationTransition commit(BetId betId, String token, Instant now) {
    ReservationTransitionRequest request =
        ReservationTransitionRequest.commit(betId, token, now, reservations, patterns, history);
    return commitLatency.record(
        () -> executeTransition(COMMIT, request.keys(), request.arguments(), "commit"));
  }

  @Override
  public ReservationTransition projectAccepted(RiskCheckCommand command, String fingerprint) {
    AcceptedProjectionRequest request =
        AcceptedProjectionRequest.from(command, fingerprint, reservations, patterns, history);
    return acceptedProjectionLatency.record(
        () ->
            executeTransition(
                PROJECT_ACCEPTED, request.keys(), request.arguments(), "accepted projection"));
  }

  @Override
  public ReservationTransition release(BetId betId, Instant now) {
    ReservationTransitionRequest request =
        ReservationTransitionRequest.release(betId, now, reservations);
    return releaseLatency.record(
        () -> executeTransition(RELEASE, request.keys(), request.arguments(), "release"));
  }

  private ReservationTransition executeTransition(
      RedisScript<String> script, List<String> keys, List<String> arguments, String operation) {
    String raw = redis.execute(script, keys, arguments.toArray());
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

  private static Timer timer(MeterRegistry meters, String operation) {
    return Timer.builder("risk.reservation.lua.latency")
        .tag("operation", operation)
        .register(Objects.requireNonNull(meters, "meters"));
  }
}
