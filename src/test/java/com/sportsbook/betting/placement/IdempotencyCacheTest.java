package com.sportsbook.betting.placement;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sportsbook.protocol.value.IdempotencyKey;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class IdempotencyCacheTest {

  @Test
  void marksOnlyCompletedBetIdentity() {
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    ValueOperations<String, String> values = mock(ValueOperations.class);
    when(redis.opsForValue()).thenReturn(values);
    UUID betId = UUID.randomUUID();

    new IdempotencyCache(redis).markProcessed(IdempotencyKey.of("request-1"), betId);

    verify(values).set("idempotency:betting:request-1", betId.toString(), IdempotencyCache.TTL);
  }
}
