package com.sportsbook.oddsfeed.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportsbook.oddsfeed.config.CacheProperties;
import com.sportsbook.oddsfeed.provider.EventSummary;
import com.sportsbook.protocol.value.EventId;
import com.sportsbook.protocol.value.MarketId;
import com.sportsbook.protocol.value.Odds;
import com.sportsbook.protocol.value.SelectionId;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

@Component
public class RedisOddsCache {

  private static final RedisScript<String> STORE_ODDS_AND_REGISTER =
      new DefaultRedisScript<>(
          """
          local eventTerminal = redis.call('EXISTS', KEYS[4]) == 1
          if eventTerminal then
            redis.call('SET', KEYS[5], 'EVENT_' .. redis.call('GET', KEYS[4]), 'NX')
          end
          local terminal = eventTerminal or redis.call('EXISTS', KEYS[5]) == 1
          local effective
          if terminal then
            effective = 'CLOSED'
            redis.call('PSETEX', KEYS[2], ARGV[2], effective)
            redis.call('HSETNX', KEYS[3], ARGV[3], 'OPEN')
          else
            redis.call('PSETEX', KEYS[1], ARGV[2], ARGV[1])
            effective = redis.call('GET', KEYS[2]) or 'OPEN'
            redis.call('HSET', KEYS[3], ARGV[3], effective)
          end
          redis.call('PEXPIRE', KEYS[3], ARGV[2])
          return effective
          """,
          String.class);

  private final StringRedisTemplate redis;
  private final ObjectMapper objectMapper;
  private final Duration ttl;

  public RedisOddsCache(
      StringRedisTemplate redis, ObjectMapper objectMapper, CacheProperties properties) {
    this.redis = redis;
    this.objectMapper = objectMapper;
    this.ttl = properties.ttl();
  }

  public void storeOdds(EventId eventId, MarketId marketId, SelectionId selectionId, Odds odds) {
    redis.execute(
        STORE_ODDS_AND_REGISTER,
        List.of(
            CacheKeys.odds(eventId, marketId, selectionId),
            CacheKeys.market(eventId, marketId),
            CacheKeys.eventMarkets(eventId),
            CacheKeys.eventTerminal(eventId),
            CacheKeys.marketTerminal(eventId, marketId)),
        odds.decimal().toPlainString(),
        ttlMillis(),
        marketId.value().toString());
  }

  public Optional<Odds> getOdds(EventId eventId, MarketId marketId, SelectionId selectionId) {
    String value = redis.opsForValue().get(CacheKeys.odds(eventId, marketId, selectionId));
    return value == null ? Optional.empty() : Optional.of(Odds.ofDecimal(value));
  }

  public void storeEvent(EventSummary summary) {
    try {
      redis
          .opsForValue()
          .set(CacheKeys.event(summary.eventId()), objectMapper.writeValueAsString(summary), ttl);
    } catch (JsonProcessingException error) {
      throw new IllegalStateException("Failed to serialize event summary", error);
    }
  }

  public Optional<EventSummary> getEvent(EventId eventId) {
    String json = redis.opsForValue().get(CacheKeys.event(eventId));
    if (json == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(objectMapper.readValue(json, EventSummary.class));
    } catch (JsonProcessingException error) {
      throw new IllegalStateException("Failed to deserialize event summary", error);
    }
  }

  private String ttlMillis() {
    return Long.toString(ttl.toMillis());
  }
}
