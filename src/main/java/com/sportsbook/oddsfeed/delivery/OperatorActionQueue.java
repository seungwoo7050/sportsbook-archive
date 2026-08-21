package com.sportsbook.oddsfeed.delivery;

import com.sportsbook.oddsfeed.cache.CacheKeys;
import com.sportsbook.oddsfeed.config.CacheProperties;
import com.sportsbook.oddsfeed.config.OperatorDeliveryProperties;
import com.sportsbook.protocol.event.MarketStatus;
import com.sportsbook.protocol.value.EventId;
import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.MarketId;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Persists operator market actions in a durable Redis Stream. */
@Component
public class OperatorActionQueue {

  private static final String IDEMPOTENCY_PREFIX = "oddsfeed:operator:idempotency:";
  private static final String ACTION_PREFIX = "oddsfeed:operator:action:";
  private static final int MAX_REASON_LENGTH = 256;

  private final StringRedisTemplate redis;
  private final OperatorDeliveryProperties properties;
  private final long marketTtlMillis;

  public OperatorActionQueue(
      StringRedisTemplate redis,
      OperatorDeliveryProperties properties,
      CacheProperties cacheProperties,
      MeterRegistry meterRegistry) {
    this.redis = redis;
    this.properties = properties;
    this.marketTtlMillis = cacheProperties.ttl().toMillis();
  }

  public OperatorActionSubmission submit(
      IdempotencyKey idempotencyKey,
      UUID actionId,
      EventId eventId,
      MarketId marketId,
      MarketStatus requestedStatus,
      String reason,
      Instant occurredAt) {
    String normalizedReason = normalizeReason(reason);
    String fingerprint =
        MarketActionFingerprint.request(
            eventId.value(), marketId.value(), requestedStatus, normalizedReason);
    String result =
        redis.execute(
            OperatorSubmissionScript.INSTANCE,
            List.of(
                idempotencyRedisKey(idempotencyKey),
                actionKey(actionId),
                CacheKeys.market(eventId, marketId),
                CacheKeys.providerMarket(eventId, marketId),
                CacheKeys.marketOverride(eventId, marketId),
                CacheKeys.marketFeedHold(eventId, marketId),
                CacheKeys.eventTerminal(eventId),
                CacheKeys.marketTerminal(eventId, marketId),
                CacheKeys.eventMarkets(eventId),
                properties.streamKey()),
            fingerprint,
            actionId.toString(),
            eventId.value().toString(),
            marketId.value().toString(),
            requestedStatus.name(),
            normalizedReason,
            Long.toString(occurredAt.toEpochMilli()),
            Long.toString(marketTtlMillis));
    if ("CONFLICT".equals(result)) {
      throw new IdempotencyConflictException();
    }
    if ("TERMINAL".equals(result)) {
      throw new TerminalMarketReopenException();
    }
    return OperatorActionSubmission.fromRedis(result);
  }

  static String idempotencyRedisKey(IdempotencyKey key) {
    return IDEMPOTENCY_PREFIX + MarketActionFingerprint.idempotencyKey(key);
  }

  private static String actionKey(UUID actionId) {
    return ACTION_PREFIX + actionId;
  }

  private static String normalizeReason(String reason) {
    if (reason == null) {
      throw new IllegalArgumentException("Operator action reason is required");
    }
    String normalized = reason.trim();
    if (normalized.isEmpty() || normalized.length() > MAX_REASON_LENGTH) {
      throw new IllegalArgumentException("Operator action reason must contain 1 to 256 characters");
    }
    return normalized;
  }
}
