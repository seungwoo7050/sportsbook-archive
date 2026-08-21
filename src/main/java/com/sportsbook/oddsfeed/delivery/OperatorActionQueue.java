package com.sportsbook.oddsfeed.delivery;

import com.sportsbook.oddsfeed.cache.CacheKeys;
import com.sportsbook.oddsfeed.config.CacheProperties;
import com.sportsbook.oddsfeed.config.OperatorDeliveryProperties;
import com.sportsbook.protocol.event.MarketStatus;
import com.sportsbook.protocol.value.EventId;
import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.MarketId;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Persists operator market actions in a durable Redis Stream. */
@Component
public class OperatorActionQueue {

  private static final String IDEMPOTENCY_PREFIX = "oddsfeed:operator:idempotency:";
  private static final String ACTION_PREFIX = "oddsfeed:operator:action:";
  private static final String SEQUENCE_PREFIX = "oddsfeed:operator:sequence:";
  private static final String COMMITTED_PREFIX = "oddsfeed:operator:committed:";
  private static final int MAX_REASON_LENGTH = 256;

  private final StringRedisTemplate redis;
  private final OperatorDeliveryProperties properties;
  private final OperatorActionCodec codec = new OperatorActionCodec();
  private final long marketTtlMillis;
  private final AtomicBoolean groupReady = new AtomicBoolean();

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
                sequenceKey(eventId, marketId),
                committedKey(eventId, marketId),
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

  public List<QueuedOperatorMarketAction> poll() {
    ensureGroup();
    List<MapRecord<String, String, String>> records =
        streamOperations()
            .read(
                Consumer.from(properties.consumerGroup(), properties.consumerName()),
                StreamReadOptions.empty().count(properties.batchSize()),
                StreamOffset.create(properties.streamKey(), ReadOffset.lastConsumed()));
    if (records == null || records.isEmpty()) {
      return List.of();
    }
    return records.stream().map(record -> codec.decode(record, false)).toList();
  }

  public void cleanup(QueuedOperatorMarketAction queued) {
    String result =
        redis.execute(
            OperatorStreamCleanupScript.INSTANCE,
            List.of(properties.streamKey()),
            properties.consumerGroup(),
            queued.recordId().getValue());
    if (result == null || !result.matches("[01]\\|[01]")) {
      throw new IllegalStateException("Malformed operator Stream cleanup result");
    }
  }

  static String idempotencyRedisKey(IdempotencyKey key) {
    return IDEMPOTENCY_PREFIX + MarketActionFingerprint.idempotencyKey(key);
  }

  static String sequenceKey(EventId eventId, MarketId marketId) {
    return SEQUENCE_PREFIX + eventId.value() + ":" + marketId.value();
  }

  static String committedKey(EventId eventId, MarketId marketId) {
    return COMMITTED_PREFIX + eventId.value() + ":" + marketId.value();
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

  private void ensureGroup() {
    if (groupReady.get()) {
      return;
    }
    try {
      redis.execute(
          (RedisCallback<String>)
              connection ->
                  connection
                      .streamCommands()
                      .xGroupCreate(
                          properties.streamKey().getBytes(StandardCharsets.UTF_8),
                          properties.consumerGroup(),
                          ReadOffset.from("0-0"),
                          true));
    } catch (DataAccessException exception) {
      if (!containsBusyGroup(exception)) {
        throw exception;
      }
    }
    groupReady.set(true);
  }

  private StreamOperations<String, String, String> streamOperations() {
    return redis.<String, String>opsForStream();
  }

  private static boolean containsBusyGroup(Throwable error) {
    Throwable current = error;
    while (current != null) {
      if (current.getMessage() != null && current.getMessage().contains("BUSYGROUP")) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }
}
