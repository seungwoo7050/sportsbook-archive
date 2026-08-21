package com.sportsbook.wallet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sportsbook.protocol.value.IdempotencyKey;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class IdempotencyCacheTest {

  private static final IdempotencyKey KEY = IdempotencyKey.of("cache:test");

  private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
  private final IdempotencyCache cache = new IdempotencyCache(redis);

  @Test
  void treatsMissesAndArbitraryMarkerContentsAsHints() {
    when(redis.hasKey(IdempotencyCache.KEY_PREFIX + KEY.value()))
        .thenReturn(null)
        .thenReturn(false)
        .thenReturn(true);

    assertThat(cache.mightContain(KEY)).isFalse();
    assertThat(cache.mightContain(KEY)).isFalse();
    assertThat(cache.mightContain(KEY)).isTrue();
    verify(redis, never()).opsForValue();
  }

  @Test
  void toleratesLookupAndWriteInfrastructureFailure() {
    when(redis.hasKey(IdempotencyCache.KEY_PREFIX + KEY.value()))
        .thenThrow(new RedisConnectionFailureException("down"));
    @SuppressWarnings("unchecked")
    ValueOperations<String, String> values = mock(ValueOperations.class);
    when(redis.opsForValue()).thenReturn(values);
    doThrow(new RedisConnectionFailureException("down"))
        .when(values)
        .set(IdempotencyCache.KEY_PREFIX + KEY.value(), "1", IdempotencyCache.TTL);

    assertThat(cache.mightContain(KEY)).isFalse();
    assertThatCode(() -> cache.mark(KEY)).doesNotThrowAnyException();
  }

  @Test
  void storesOnlyAnExpiringExistenceMarker() {
    @SuppressWarnings("unchecked")
    ValueOperations<String, String> values = mock(ValueOperations.class);
    when(redis.opsForValue()).thenReturn(values);

    cache.mark(KEY);

    verify(values).set(IdempotencyCache.KEY_PREFIX + KEY.value(), "1", IdempotencyCache.TTL);
  }
}
