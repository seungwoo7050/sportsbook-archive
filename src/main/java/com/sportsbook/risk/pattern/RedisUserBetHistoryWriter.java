package com.sportsbook.risk.pattern;

import com.sportsbook.risk.counter.RedisLuaScriptLoader;
import com.sportsbook.risk.policy.RiskPatternProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/** Executes one atomic Redis projection for all confirmed pattern facts. */
@Component
public final class RedisUserBetHistoryWriter implements UserBetHistoryWriter {
  private static final RedisScript<List> SCRIPT =
      RedisLuaScriptLoader.listScript("history-record.lua");

  private final StringRedisTemplate redis;
  private final RiskPatternProperties patterns;
  private final RiskHistoryProperties history;

  public RedisUserBetHistoryWriter(
      StringRedisTemplate redis, RiskPatternProperties patterns, RiskHistoryProperties history) {
    this.redis = Objects.requireNonNull(redis, "redis");
    this.patterns = Objects.requireNonNull(patterns, "patterns");
    this.history = Objects.requireNonNull(history, "history");
  }

  @Override
  @SuppressWarnings("unchecked")
  public WriteResult record(PatternContext context) {
    Objects.requireNonNull(context, "context");
    List<String> keys = new ArrayList<>();
    keys.add(HistoryKeys.bets(context.userId()));
    keys.add(HistoryKeys.stakes(context.userId(), context.stake().currency()));
    context
        .selections()
        .forEach(selection -> keys.add(HistoryKeys.selection(context.userId(), selection)));
    List<String> result =
        (List<String>)
            (List<?>)
                redis.execute(
                    SCRIPT,
                    keys,
                    Long.toString(context.evaluatedAt().toEpochMilli()),
                    HistoryKeys.betMember(context.betId()),
                    HistoryKeys.stakeMember(context.betId(), context.stake().amount()),
                    Long.toString(patterns.rapidBetting().window().toMillis()),
                    Integer.toString(history.maxStakeSamples()),
                    Long.toString(patterns.repeatedSelection().window().toMillis()),
                    Long.toString(history.idleRetention().toMillis()));
    if (result == null || result.size() != 2) {
      throw new IllegalStateException("unexpected history projection result");
    }
    return new WriteResult(parseFlag(result.get(0)), parseFlag(result.get(1)));
  }

  private static boolean parseFlag(String value) {
    if (!"0".equals(value) && !"1".equals(value)) {
      throw new IllegalStateException("malformed history projection result");
    }
    return "1".equals(value);
  }
}
