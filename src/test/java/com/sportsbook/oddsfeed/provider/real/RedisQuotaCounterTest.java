package com.sportsbook.oddsfeed.provider.real;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RedisQuotaCounterTest {

  private static final String KEY = RedisQuotaCounter.KEY_PREFIX + "2026-05";

  private final RecordingRedis redis = new RecordingRedis();
  private final RedisQuotaCounter counter =
      new RedisQuotaCounter(
          redis, Clock.fixed(Instant.parse("2026-05-28T10:00:00Z"), ZoneOffset.UTC));

  @Test
  void incrementsTheCurrentUtcMonthAndRefreshesExpiry() {
    redis.incremented = 7L;

    assertThat(counter.increment()).isEqualTo(7);
    assertThat(redis.incrementedKey).isEqualTo(KEY);
    assertThat(redis.expiredKey).isEqualTo(KEY);
    assertThat(redis.expiry).isEqualTo(RedisQuotaCounter.TTL);
  }

  @Test
  void readsCurrentUsageWithoutMutation() {
    redis.stored = "19";

    assertThat(counter.current()).isEqualTo(19);
    assertThat(redis.readKey).isEqualTo(KEY);
  }

  private static final class RecordingRedis extends StringRedisTemplate {
    private Long incremented;
    private String stored;
    private String incrementedKey;
    private String readKey;
    private String expiredKey;
    private Duration expiry;

    @Override
    @SuppressWarnings("unchecked")
    public ValueOperations<String, String> opsForValue() {
      return (ValueOperations<String, String>)
          Proxy.newProxyInstance(
              ValueOperations.class.getClassLoader(),
              new Class<?>[] {ValueOperations.class},
              (proxy, method, args) -> {
                if ("increment".equals(method.getName())) {
                  incrementedKey = (String) args[0];
                  return incremented;
                }
                if ("get".equals(method.getName())) {
                  readKey = (String) args[0];
                  return stored;
                }
                return null;
              });
    }

    @Override
    public Boolean expire(String key, Duration timeout) {
      expiredKey = key;
      expiry = timeout;
      return true;
    }
  }
}
