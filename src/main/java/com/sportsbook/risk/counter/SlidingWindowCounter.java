package com.sportsbook.risk.counter;

import com.sportsbook.protocol.value.BetId;
import com.sportsbook.risk.policy.SafeRedisNumber;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/** Strict Java boundary for the atomic committed sliding-window script. */
public final class SlidingWindowCounter {
  private static final RedisScript<List> SCRIPT =
      RedisLuaScriptLoader.listScript("sliding-window.lua");
  private static final Duration TTL_MARGIN = Duration.ofMinutes(5);

  private final StringRedisTemplate redis;
  private final Clock clock;

  public SlidingWindowCounter(StringRedisTemplate redis, Clock clock) {
    this.redis = Objects.requireNonNull(redis, "redis");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  public WindowResult record(
      LimitKeys.Keys keys, BetId betId, long amount, Duration window, Instant at) {
    SafeRedisNumber.requirePositive(amount, "amount");
    return execute(keys, "RECORD", window, at, LimitKeys.member(betId, amount), amount);
  }

  public long read(LimitKeys.Keys keys, Duration window) {
    return read(keys, window, clock.instant());
  }

  public long read(LimitKeys.Keys keys, Duration window, Instant at) {
    return execute(keys, "READ", window, at, "", 0).total();
  }

  @SuppressWarnings("unchecked")
  private WindowResult execute(
      LimitKeys.Keys keys, String mode, Duration window, Instant at, String member, long amount) {
    Objects.requireNonNull(keys, "keys");
    Objects.requireNonNull(window, "window");
    Objects.requireNonNull(at, "at");
    if (window.isZero() || window.isNegative()) {
      throw new IllegalArgumentException("window must be positive");
    }
    List<String> result =
        (List<String>)
            (List<?>)
                redis.execute(
                    SCRIPT,
                    List.of(keys.entries(), keys.sum()),
                    mode,
                    Long.toString(at.toEpochMilli()),
                    Long.toString(window.toMillis()),
                    Long.toString(window.plus(TTL_MARGIN).toMillis()),
                    member,
                    Long.toString(amount));
    if (result == null || result.size() != 2) {
      throw new IllegalStateException("unexpected sliding-window result");
    }
    try {
      long total = Long.parseLong(result.get(0));
      int added = Integer.parseInt(result.get(1));
      SafeRedisNumber.requireNonNegative(total, "total");
      if (added != 0 && added != 1) {
        throw new IllegalStateException("unexpected sliding-window insertion result");
      }
      return new WindowResult(total, added == 1);
    } catch (NumberFormatException exception) {
      throw new IllegalStateException("malformed sliding-window result", exception);
    }
  }

  public record WindowResult(long total, boolean added) {}
}
