package com.sportsbook.oddsfeed.provider.real;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("real")
public class RedisQuotaCounter implements QuotaCounter {

  static final String KEY_PREFIX = "oddsfeed:provider-quota:";
  static final Duration TTL = Duration.ofDays(35);
  private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

  private final StringRedisTemplate redis;
  private final Clock clock;

  public RedisQuotaCounter(StringRedisTemplate redis, Clock clock) {
    this.redis = redis;
    this.clock = clock;
  }

  @Override
  public long increment() {
    String key = currentKey();
    Long value = redis.opsForValue().increment(key);
    redis.expire(key, TTL);
    return value == null ? 0 : value;
  }

  @Override
  public long current() {
    String value = redis.opsForValue().get(currentKey());
    return value == null ? 0 : Long.parseLong(value);
  }

  String currentKey() {
    return KEY_PREFIX + MONTH.format(clock.instant().atZone(ZoneOffset.UTC));
  }
}
