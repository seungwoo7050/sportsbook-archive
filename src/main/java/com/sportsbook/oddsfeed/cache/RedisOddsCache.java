package com.sportsbook.oddsfeed.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportsbook.oddsfeed.config.CacheProperties;
import com.sportsbook.oddsfeed.provider.EventSummary;
import com.sportsbook.protocol.event.EventLifecycleStatus;
import com.sportsbook.protocol.event.MarketStatus;
import com.sportsbook.protocol.value.EventId;
import com.sportsbook.protocol.value.MarketId;
import com.sportsbook.protocol.value.Odds;
import com.sportsbook.protocol.value.SelectionId;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
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

  private static final RedisScript<String> STORE_OPERATOR_MARKET_STATUS =
      new DefaultRedisScript<>(
          """
          local requested = ARGV[1]
          local eventTerminal = redis.call('EXISTS', KEYS[6]) == 1
          if eventTerminal then
            redis.call('SET', KEYS[5], 'EVENT_' .. redis.call('GET', KEYS[6]), 'NX')
          end
          if requested == 'OPEN' then
            redis.call('DEL', KEYS[3])
          else
            redis.call('SET', KEYS[3], requested)
          end
          local provider = redis.call('GET', KEYS[2]) or 'OPEN'
          if provider == 'CLOSED' and not eventTerminal then
            redis.call('SET', KEYS[5], 'MARKET_CLOSED')
          end
          local terminal = eventTerminal or redis.call('EXISTS', KEYS[5]) == 1
          local effective
          if terminal then
            effective = 'CLOSED'
          elseif requested ~= 'OPEN' then
            effective = requested
          elseif redis.call('EXISTS', KEYS[4]) == 1 then
            effective = 'SUSPENDED'
          else
            effective = provider
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

  private static final RedisScript<String> PROJECT_LATEST_ODDS =
      new DefaultRedisScript<>(
          """
          local eventTerminal = redis.call('EXISTS', KEYS[7]) == 1
          if eventTerminal then
            redis.call('SET', KEYS[6], 'EVENT_' .. redis.call('GET', KEYS[7]), 'NX')
          end
          if eventTerminal or redis.call('EXISTS', KEYS[6]) == 1 then
            redis.call('PSETEX', KEYS[2], ARGV[2], 'CLOSED')
            redis.call('HSETNX', KEYS[8], ARGV[3], 'OPEN')
            redis.call('PEXPIRE', KEYS[8], ARGV[2])
            return 'CLOSED'
          end
          local heldAt = redis.call('GET', KEYS[5])
          if not heldAt or tonumber(ARGV[4]) >= tonumber(heldAt) then
            redis.call('PSETEX', KEYS[1], ARGV[2], ARGV[1])
            if redis.call('EXISTS', KEYS[3]) == 0 then
              redis.call('PSETEX', KEYS[3], ARGV[2], 'OPEN')
            end
            if ARGV[5] == 'HOLD' then
              redis.call('PSETEX', KEYS[5], ARGV[2], ARGV[4])
            else
              redis.call('DEL', KEYS[5])
            end
          end
          local effective = redis.call('GET', KEYS[4])
          if not effective then
            effective = redis.call('EXISTS', KEYS[5]) == 1
              and 'SUSPENDED' or (redis.call('GET', KEYS[3]) or 'OPEN')
          end
          redis.call('PSETEX', KEYS[2], ARGV[2], effective)
          redis.call('HSET', KEYS[8], ARGV[3], effective)
          redis.call('PEXPIRE', KEYS[8], ARGV[2])
          return effective
          """,
          String.class);

  private static final RedisScript<String> CLOSE_EVENT_MARKETS =
      new DefaultRedisScript<>(
          """
          redis.call('SET', KEYS[1], ARGV[1], 'NX')
          local terminalStatus = redis.call('GET', KEYS[1])
          local inventory = redis.call('HGETALL', KEYS[2])
          local closed = {}
          for index = 1, #inventory, 2 do
            local marketId = inventory[index]
            local previous = inventory[index + 1]
            local effectiveKey = ARGV[3] .. marketId
            local providerKey = ARGV[4] .. marketId
            local terminalKey = ARGV[5] .. marketId
            redis.call('SET', terminalKey, 'EVENT_' .. terminalStatus, 'NX')
            redis.call('PSETEX', providerKey, ARGV[2], 'CLOSED')
            redis.call('PSETEX', effectiveKey, ARGV[2], 'CLOSED')
            if previous ~= 'CLOSED' then
              table.insert(closed, marketId .. '|' .. previous)
            end
          end
          redis.call('PEXPIRE', KEYS[2], ARGV[2])
          return table.concat(closed, '\\n')
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

  public MarketStatus holdLatestOdds(
      EventId eventId, MarketId marketId, SelectionId selectionId, Odds odds, Instant observedAt) {
    return executeOddsProjection(eventId, marketId, selectionId, odds, observedAt, "HOLD");
  }

  public MarketStatus projectLatestOdds(
      EventId eventId, MarketId marketId, SelectionId selectionId, Odds odds, Instant observedAt) {
    return executeOddsProjection(eventId, marketId, selectionId, odds, observedAt, "RELEASE");
  }

  public boolean isFeedHeld(EventId eventId, MarketId marketId) {
    return Boolean.TRUE.equals(redis.hasKey(CacheKeys.marketFeedHold(eventId, marketId)));
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

  public MarketStatus storeOperatorMarketStatus(
      EventId eventId, MarketId marketId, MarketStatus status) {
    return requireStatus(
        redis.execute(
            STORE_OPERATOR_MARKET_STATUS,
            marketKeys(eventId, marketId),
            status.name(),
            ttlMillis(),
            marketId.value().toString()));
  }

  public Optional<MarketStatus> getMarketOverride(EventId eventId, MarketId marketId) {
    String value = redis.opsForValue().get(CacheKeys.marketOverride(eventId, marketId));
    return value == null ? Optional.empty() : Optional.of(MarketStatus.valueOf(value));
  }

  public Optional<MarketStatus> getMarketStatus(EventId eventId, MarketId marketId) {
    String value = redis.opsForValue().get(CacheKeys.market(eventId, marketId));
    return value == null ? Optional.empty() : Optional.of(MarketStatus.valueOf(value));
  }

  public boolean isMarketTerminal(EventId eventId, MarketId marketId) {
    return Boolean.TRUE.equals(redis.hasKey(CacheKeys.marketTerminal(eventId, marketId)));
  }

  public Optional<EventLifecycleStatus> getEventTerminal(EventId eventId) {
    String value = redis.opsForValue().get(CacheKeys.eventTerminal(eventId));
    return value == null ? Optional.empty() : Optional.of(EventLifecycleStatus.valueOf(value));
  }

  public Map<MarketId, MarketStatus> getRegisteredMarkets(EventId eventId) {
    Map<Object, Object> entries = redis.opsForHash().entries(CacheKeys.eventMarkets(eventId));
    Map<MarketId, MarketStatus> markets =
        new TreeMap<>(Comparator.comparing(id -> id.value().toString()));
    entries.forEach(
        (id, status) ->
            markets.put(
                new MarketId(UUID.fromString(id.toString())),
                MarketStatus.valueOf(status.toString())));
    return Collections.unmodifiableMap(new LinkedHashMap<>(markets));
  }

  public Map<UUID, MarketStatus> closeEventMarkets(
      EventId eventId, EventLifecycleStatus terminalStatus) {
    String encoded =
        redis.execute(
            CLOSE_EVENT_MARKETS,
            List.of(CacheKeys.eventTerminal(eventId), CacheKeys.eventMarkets(eventId)),
            terminalStatus.name(),
            ttlMillis(),
            "market:" + eventId.value() + ":",
            "market:provider:" + eventId.value() + ":",
            "market:terminal:" + eventId.value() + ":");
    if (encoded == null || encoded.isBlank()) {
      return Map.of();
    }
    Map<UUID, MarketStatus> closed = new TreeMap<>();
    for (String entry : encoded.split("\\n")) {
      String[] parts = entry.split("\\|", 2);
      closed.put(UUID.fromString(parts[0]), MarketStatus.valueOf(parts[1]));
    }
    return Collections.unmodifiableMap(new LinkedHashMap<>(closed));
  }

  private MarketStatus executeOddsProjection(
      EventId eventId,
      MarketId marketId,
      SelectionId selectionId,
      Odds odds,
      Instant observedAt,
      String mode) {
    return requireStatus(
        redis.execute(
            PROJECT_LATEST_ODDS,
            List.of(
                CacheKeys.odds(eventId, marketId, selectionId),
                CacheKeys.market(eventId, marketId),
                CacheKeys.providerMarket(eventId, marketId),
                CacheKeys.marketOverride(eventId, marketId),
                CacheKeys.marketFeedHold(eventId, marketId),
                CacheKeys.marketTerminal(eventId, marketId),
                CacheKeys.eventTerminal(eventId),
                CacheKeys.eventMarkets(eventId)),
            odds.decimal().toPlainString(),
            ttlMillis(),
            marketId.value().toString(),
            Long.toString(observedAt.toEpochMilli()),
            mode));
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
