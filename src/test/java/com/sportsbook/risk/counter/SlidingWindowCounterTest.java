package com.sportsbook.risk.counter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sportsbook.protocol.value.BetId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

class SlidingWindowCounterTest {
  private static final Instant NOW = Instant.parse("2026-01-02T03:04:05Z");
  private static final LimitKeys.Keys KEYS = new LimitKeys.Keys("entries", "sum");
  private static final BetId BET =
      BetId.of(UUID.fromString("00000000-0000-0000-0000-000000000002"));

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void recordsTypedEntriesAndReadsAtTheInjectedClock() {
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
        .thenReturn(List.of("75", "1"), List.of("75", "0"));
    SlidingWindowCounter counter =
        new SlidingWindowCounter(redis, Clock.fixed(NOW, ZoneOffset.UTC));

    assertThat(counter.record(KEYS, BET, 75, Duration.ofMinutes(1), NOW))
        .isEqualTo(new SlidingWindowCounter.WindowResult(75, true));
    assertThat(counter.read(KEYS, Duration.ofMinutes(1))).isEqualTo(75);
    verify(redis)
        .execute(
            any(RedisScript.class),
            org.mockito.ArgumentMatchers.eq(List.of("entries", "sum")),
            org.mockito.ArgumentMatchers.eq("RECORD"),
            org.mockito.ArgumentMatchers.eq(Long.toString(NOW.toEpochMilli())),
            org.mockito.ArgumentMatchers.eq("60000"),
            org.mockito.ArgumentMatchers.eq("360000"),
            org.mockito.ArgumentMatchers.eq(BET.value() + "|75"),
            org.mockito.ArgumentMatchers.eq("75"));
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void rejectsInvalidInputsAndMalformedScriptResults() {
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    SlidingWindowCounter counter = new SlidingWindowCounter(redis, Clock.systemUTC());

    assertThatThrownBy(() -> counter.read(KEYS, Duration.ZERO))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> counter.record(KEYS, BET, 0, Duration.ofMinutes(1), NOW))
        .isInstanceOf(IllegalArgumentException.class);
    when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
        .thenReturn(List.of("not-a-number", "1"));
    assertThatThrownBy(() -> counter.read(KEYS, Duration.ofMinutes(1), NOW))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("malformed sliding-window result");
  }
}
