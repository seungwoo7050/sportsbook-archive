package com.sportsbook.wallet.service;

import com.sportsbook.protocol.value.IdempotencyKey;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Best-effort existence marker; PostgreSQL remains the only owner of request outcomes. */
@Component
public class IdempotencyCache {

  static final Duration TTL = Duration.ofHours(24L);
  static final String KEY_PREFIX = "idempotency:wallet:";

  private static final Logger log = LoggerFactory.getLogger(IdempotencyCache.class);

  private final StringRedisTemplate redis;

  public IdempotencyCache(StringRedisTemplate redis) {
    this.redis = redis;
  }

  public boolean mightContain(IdempotencyKey key) {
    try {
      return Boolean.TRUE.equals(redis.hasKey(redisKey(key)));
    } catch (DataAccessException unavailable) {
      log.warn("Redis idempotency lookup failed; using PostgreSQL", unavailable);
      return false;
    }
  }

  public void mark(IdempotencyKey key) {
    try {
      redis.opsForValue().set(redisKey(key), "1", TTL);
    } catch (DataAccessException unavailable) {
      log.warn("Redis idempotency marker failed after PostgreSQL outcome", unavailable);
    }
  }

  private static String redisKey(IdempotencyKey key) {
    return KEY_PREFIX + key.value();
  }
}
