package com.sportsbook.risk.limit;

import com.sportsbook.protocol.value.UserId;
import com.sportsbook.risk.policy.SafeRedisNumber;
import java.util.Objects;
import java.util.OptionalLong;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Redis hash implementation of authoritative user-specific limit overrides. */
@Component
public final class RedisLimitOverrideStore implements LimitOverrideStore {
  private final StringRedisTemplate redis;

  public RedisLimitOverrideStore(StringRedisTemplate redis) {
    this.redis = Objects.requireNonNull(redis, "redis");
  }

  @Override
  public OptionalLong find(UserId userId, LimitOverrideField field) {
    String value =
        (String)
            redis
                .opsForHash()
                .get(
                    LimitOverrideKeys.user(userId),
                    Objects.requireNonNull(field, "field").redisField());
    if (value == null) {
      return OptionalLong.empty();
    }
    try {
      return OptionalLong.of(
          SafeRedisNumber.requireNonNegative(Long.parseLong(value), "stored override"));
    } catch (NumberFormatException exception) {
      throw new IllegalStateException("stored override is not an integer", exception);
    }
  }

  @Override
  public void set(UserId userId, LimitOverrideField field, long value) {
    SafeRedisNumber.requireNonNegative(value, "override");
    redis
        .opsForHash()
        .put(
            LimitOverrideKeys.user(userId),
            Objects.requireNonNull(field, "field").redisField(),
            Long.toString(value));
  }

  @Override
  public void clear(UserId userId, LimitOverrideField field) {
    redis
        .opsForHash()
        .delete(
            LimitOverrideKeys.user(userId), Objects.requireNonNull(field, "field").redisField());
  }

  static String key(UserId userId) {
    return LimitOverrideKeys.user(userId);
  }
}
