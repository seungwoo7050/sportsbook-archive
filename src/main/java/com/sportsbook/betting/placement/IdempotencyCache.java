package com.sportsbook.betting.placement;

import com.sportsbook.protocol.value.IdempotencyKey;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class IdempotencyCache {

  private static final Logger LOG = LoggerFactory.getLogger(IdempotencyCache.class);
  static final Duration TTL = Duration.ofHours(24);
  static final String PREFIX = "idempotency:betting:";

  private final StringRedisTemplate redis;

  public IdempotencyCache(StringRedisTemplate redis) {
    this.redis = redis;
  }

  public void markProcessed(IdempotencyKey key, UUID betId) {
    try {
      redis.opsForValue().set(PREFIX + key.value(), betId.toString(), TTL);
    } catch (DataAccessException exception) {
      LOG.warn("Could not update idempotency cache for bet {}", betId);
    }
  }
}
