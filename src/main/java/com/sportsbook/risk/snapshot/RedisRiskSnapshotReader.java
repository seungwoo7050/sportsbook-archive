package com.sportsbook.risk.snapshot;

import com.sportsbook.risk.counter.RedisLuaScriptLoader;
import com.sportsbook.risk.pattern.PatternContext;
import com.sportsbook.risk.policy.RiskPatternProperties;
import com.sportsbook.risk.reservation.RiskReservationProperties;
import java.util.Objects;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/** Standalone-Redis implementation of the combined snapshot boundary. */
@Component
public final class RedisRiskSnapshotReader implements RiskSnapshotReader {
  private static final RedisScript<String> SCRIPT =
      RedisLuaScriptLoader.stringScript("risk-snapshot.lua");

  private final StringRedisTemplate redis;
  private final RiskPatternProperties patterns;
  private final RiskReservationProperties reservations;
  private final RiskSnapshotWireMapper mapper;

  public RedisRiskSnapshotReader(
      StringRedisTemplate redis,
      RiskPatternProperties patterns,
      RiskReservationProperties reservations,
      RiskSnapshotWireMapper mapper) {
    this.redis = Objects.requireNonNull(redis, "redis");
    this.patterns = Objects.requireNonNull(patterns, "patterns");
    this.reservations = Objects.requireNonNull(reservations, "reservations");
    this.mapper = Objects.requireNonNull(mapper, "mapper");
  }

  @Override
  public RiskSnapshot read(PatternContext context) {
    RiskSnapshotScriptRequest request =
        RiskSnapshotScriptRequest.from(context, patterns, reservations);
    String raw = redis.execute(SCRIPT, request.keys(), request.arguments().toArray());
    return mapper.map(raw, context.selections()).snapshot();
  }
}
