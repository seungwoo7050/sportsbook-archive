package com.sportsbook.oddsfeed.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportsbook.oddsfeed.config.CacheProperties;
import com.sportsbook.oddsfeed.provider.EventSummary;
import com.sportsbook.protocol.event.MarketStatus;
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

  private static final RedisScript<String> STORE_PROVIDER_MARKET_STATUS =
      new DefaultRedisScript<>(
          """
          local requested = ARGV[1]
          local eventTerminal = redis.call('EXISTS', KEYS[6]) == 1
          if eventTerminal then
            redis.call('SET', KEYS[5], 'EVENT_' .. redis.call('GET', KEYS[6]), 'NX')
          elseif requested == 'CLOSED' then
            redis.call('SET', KEYS[5], 'MARKET_CLOSED')
            redis.call('PSETEX', KEYS[2], ARGV[2], 'CLOSED')
          end
          local terminal = eventTerminal or redis.call('EXISTS', KEYS[5]) == 1
          local effective
          if terminal then
            effective = 'CLOSED'
          else
            redis.call('PSETEX', KEYS[2], ARGV[2], requested)
            effective = redis.call('GET', KEYS[3])
            if not effective then
              effective = redis.call('EXISTS', KEYS[4]) == 1 and 'SUSPENDED' or requested
            end
          end
          redis.call('PSETEX', KEYS[1], ARGV[2], effective)
          if eventTerminal then
            redis.call('HSETNX', KEYS[7], ARGV[3], 'OPEN')
          else
            redis.call('HSET', KEYS[7], ARGV[3], effective)
          end
          redis.call('PEXPIRE', KEYS[7], ARGV[2])
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

  public MarketStatus storeProviderMarketStatus(
      EventId eventId, MarketId marketId, MarketStatus status) {
    return requireStatus(
        redis.execute(
            STORE_PROVIDER_MARKET_STATUS,
            marketKeys(eventId, marketId),
            status.name(),
            ttlMillis(),
            marketId.value().toString()));
  }

  public Optional<MarketStatus> getMarketStatus(EventId eventId, MarketId marketId) {
    String value = redis.opsForValue().get(CacheKeys.market(eventId, marketId));
    return value == null ? Optional.empty() : Optional.of(MarketStatus.valueOf(value));
  }

  private static List<String> marketKeys(EventId eventId, MarketId marketId) {
    return List.of(
        CacheKeys.market(eventId, marketId),
        CacheKeys.providerMarket(eventId, marketId),
        CacheKeys.marketOverride(eventId, marketId),
        CacheKeys.marketFeedHold(eventId, marketId),
        CacheKeys.marketTerminal(eventId, marketId),
        CacheKeys.eventTerminal(eventId),
        CacheKeys.eventMarkets(eventId));
  }

  private String ttlMillis() {
    return Long.toString(ttl.toMillis());
  }

  private static MarketStatus requireStatus(String value) {
    if (value == null) {
      throw new IllegalStateException("Redis market projection returned no result");
    }
    return MarketStatus.valueOf(value);
  }
}
