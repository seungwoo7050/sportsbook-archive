package com.sportsbook.oddsfeed.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportsbook.oddsfeed.config.CacheProperties;
import com.sportsbook.oddsfeed.provider.EventSummary;
import com.sportsbook.oddsfeed.provider.Sport;
import com.sportsbook.protocol.event.EventLifecycleStatus;
import com.sportsbook.protocol.value.EventId;
import com.sportsbook.protocol.value.MarketId;
import com.sportsbook.protocol.value.Odds;
import com.sportsbook.protocol.value.SelectionId;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

class RedisOddsCacheTest {

  private final EventId eventId = new EventId(UUID.randomUUID());
  private final MarketId marketId = new MarketId(UUID.randomUUID());
  private final SelectionId selectionId = new SelectionId(UUID.randomUUID());
  private RecordingRedis redis;
  private RedisOddsCache cache;

  @BeforeEach
  void setUp() {
    redis = new RecordingRedis();
    cache =
        new RedisOddsCache(
            redis,
            new ObjectMapper().findAndRegisterModules(),
            new CacheProperties(Duration.ofHours(24)));
  }

  @Test
  void storesOddsWithRegistryAndTerminalGuards() {
    Odds odds = Odds.ofDecimal("1.85");

    cache.storeOdds(eventId, marketId, selectionId, odds);

    assertThat(cache.getOdds(eventId, marketId, selectionId)).contains(odds);
    assertThat(redis.keys)
        .containsExactly(
            CacheKeys.odds(eventId, marketId, selectionId),
            CacheKeys.market(eventId, marketId),
            CacheKeys.eventMarkets(eventId),
            CacheKeys.eventTerminal(eventId),
            CacheKeys.marketTerminal(eventId, marketId));
  }

  @Test
  void roundTripsEventSummaryProjection() {
    EventSummary summary =
        new EventSummary(
            eventId,
            Sport.FOOTBALL,
            "Premier League",
            "Manchester United",
            "Chelsea",
            Instant.parse("2026-06-01T18:00:00Z"),
            EventLifecycleStatus.SCHEDULED);

    cache.storeEvent(summary);

    assertThat(cache.getEvent(eventId)).contains(summary);
  }

  private static final class RecordingRedis extends StringRedisTemplate {
    private final Map<String, String> values = new HashMap<>();
    private List<String> keys = List.of();

    @Override
    @SuppressWarnings("unchecked")
    public <T> T execute(RedisScript<T> script, List<String> keys, Object... args) {
      this.keys = List.copyOf(keys);
      values.put(keys.get(0), args[0].toString());
      return (T) "OPEN";
    }

    @Override
    @SuppressWarnings("unchecked")
    public ValueOperations<String, String> opsForValue() {
      return (ValueOperations<String, String>)
          Proxy.newProxyInstance(
              ValueOperations.class.getClassLoader(),
              new Class<?>[] {ValueOperations.class},
              (proxy, method, args) -> {
                if ("get".equals(method.getName())) {
                  return values.get(args[0].toString());
                }
                if ("set".equals(method.getName())) {
                  values.put(args[0].toString(), args[1].toString());
                }
                return null;
              });
    }
  }
}
