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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
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
  private static final int MAPPING_RETENTION_DAYS = 7;
  private static final long COMPLETED_MAPPING_TTL_MILLIS =
      Duration.ofDays(MAPPING_RETENTION_DAYS).toMillis();
  private static final int MAX_REASON_LENGTH = 256;

  private final StringRedisTemplate redis;
  private final OperatorDeliveryProperties properties;
  private final OperatorActionCodec codec = new OperatorActionCodec();
  private final long marketTtlMillis;
  private final AtomicBoolean groupReady = new AtomicBoolean();
  private final AtomicLong pendingCount = new AtomicLong();

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
    PendingMessages pending = pendingMessages();
    List<MapRecord<String, String, String>> records = claimExpired(pending);
    boolean reclaimed = !records.isEmpty();
    if (records.isEmpty() && pending.isEmpty()) {
      records =
          streamOperations()
              .read(
                  Consumer.from(properties.consumerGroup(), properties.consumerName()),
                  StreamReadOptions.empty().count(properties.batchSize()),
                  StreamOffset.create(properties.streamKey(), ReadOffset.lastConsumed()));
    }
    pendingCount.set(
        streamOperations()
            .pending(properties.streamKey(), properties.consumerGroup())
            .getTotalPendingMessages());
    if (records == null || records.isEmpty()) {
      return List.of();
    }
    boolean reclaimedRecord = reclaimed;
    return records.stream().map(record -> codec.decode(record, reclaimedRecord)).toList();
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
    if (result.charAt(0) == '1') {
      pendingCount.updateAndGet(current -> Math.max(0, current - 1));
    }
  }

  public long pendingCount() {
    return pendingCount.get();
  }

  public DeliveryState deliveryState(OperatorMarketAction action) {
    String raw = redis.opsForValue().get(committedKey(action.eventId(), action.marketId()));
    long committed = raw == null ? 0 : Long.parseLong(raw);
    if (committed >= action.sequence()) {
      return DeliveryState.COMPLETED;
    }
    return committed == action.predecessor() ? DeliveryState.READY : DeliveryState.BLOCKED;
  }

  public OperatorDeliveryDecision deliveryDecision(OperatorMarketAction action) {
    String result =
        redis.execute(
            OperatorDeliveryDecisionScript.INSTANCE,
            List.of(
                committedKey(action.eventId(), action.marketId()),
                sequenceKey(action.eventId(), action.marketId()),
                CacheKeys.providerMarket(action.eventId(), action.marketId()),
                CacheKeys.eventTerminal(action.eventId()),
                CacheKeys.marketTerminal(action.eventId(), action.marketId()),
                CacheKeys.marketFeedHold(action.eventId(), action.marketId())),
            Long.toString(action.sequence()),
            Long.toString(action.predecessor()),
            action.requestedStatus().name());
    return parseDeliveryDecision(result);
  }

  public Completion complete(OperatorMarketAction action) {
    String result =
        redis.execute(
            OperatorCompletionScript.INSTANCE,
            List.of(
                committedKey(action.eventId(), action.marketId()),
                sequenceKey(action.eventId(), action.marketId()),
                CacheKeys.market(action.eventId(), action.marketId()),
                CacheKeys.providerMarket(action.eventId(), action.marketId()),
                CacheKeys.marketOverride(action.eventId(), action.marketId()),
                actionKey(action.actionId()),
                CacheKeys.eventTerminal(action.eventId()),
                CacheKeys.marketTerminal(action.eventId(), action.marketId()),
                CacheKeys.marketFeedHold(action.eventId(), action.marketId()),
                CacheKeys.eventMarkets(action.eventId())),
            Long.toString(action.sequence()),
            Long.toString(action.predecessor()),
            action.requestedStatus().name(),
            Long.toString(marketTtlMillis),
            Long.toString(COMPLETED_MAPPING_TTL_MILLIS),
            action.marketId().value().toString());
    if (result == null) {
      throw new IllegalStateException("Operator action completion returned no result");
    }
    return Completion.valueOf(result);
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

  private PendingMessages pendingMessages() {
    return streamOperations()
        .pending(
            properties.streamKey(),
            properties.consumerGroup(),
            Range.unbounded(),
            properties.batchSize());
  }

  private List<MapRecord<String, String, String>> claimExpired(PendingMessages pending) {
    List<RecordId> claimable = new ArrayList<>();
    for (PendingMessage message : pending) {
      if (message.getElapsedTimeSinceLastDelivery().compareTo(properties.claimIdle()) < 0) {
        break;
      }
      claimable.add(message.getId());
    }
    if (claimable.isEmpty()) {
      return List.of();
    }
    List<MapRecord<String, String, String>> claimed =
        streamOperations()
            .claim(
                properties.streamKey(),
                properties.consumerGroup(),
                properties.consumerName(),
                properties.claimIdle(),
                claimable.toArray(RecordId[]::new));
    return claimed == null ? List.of() : claimed;
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

  private static OperatorDeliveryDecision parseDeliveryDecision(String result) {
    if (result == null) {
      throw new IllegalStateException("Operator delivery decision returned no result");
    }
    if (result.startsWith("PUBLISH|")) {
      return new OperatorDeliveryDecision(
          OperatorDeliveryDecision.Outcome.PUBLISH,
          MarketStatus.valueOf(result.substring("PUBLISH|".length())));
    }
    return new OperatorDeliveryDecision(OperatorDeliveryDecision.Outcome.valueOf(result), null);
  }

  public enum DeliveryState {
    READY,
    BLOCKED,
    COMPLETED
  }

  public enum Completion {
    APPLIED,
    SUPERSEDED,
    COMPLETED,
    BLOCKED
  }
}
