package com.sportsbook.oddsfeed.delivery;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportsbook.oddsfeed.config.CriticalDeliveryProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class CriticalEventQueue {

  private static final String PAYLOAD_FIELD = "payload";

  private final StringRedisTemplate redis;
  private final ObjectMapper objectMapper;
  private final CriticalDeliveryProperties properties;
  private final Counter enqueued;
  private final Counter failures;
  private final AtomicBoolean healthy = new AtomicBoolean(true);
  private final AtomicLong pendingCount = new AtomicLong();
  private final AtomicBoolean groupReady = new AtomicBoolean();

  public CriticalEventQueue(
      StringRedisTemplate redis,
      ObjectMapper objectMapper,
      CriticalDeliveryProperties properties,
      MeterRegistry meterRegistry) {
    this.redis = redis;
    this.objectMapper = objectMapper;
    this.properties = properties;
    this.enqueued = meterRegistry.counter("oddsfeed.critical.delivery.enqueued");
    this.failures = meterRegistry.counter("oddsfeed.critical.delivery.failure");
    meterRegistry.gauge("oddsfeed.critical.delivery.pending", pendingCount);
  }

  public RecordId enqueue(CriticalEvent event) {
    try {
      String payload = objectMapper.writeValueAsString(event);
      RecordId recordId =
          streamOperations().add(properties.streamKey(), Map.of(PAYLOAD_FIELD, payload));
      if (recordId == null) {
        throw new IllegalStateException("Redis did not return a critical event record id");
      }
      healthy.set(true);
      enqueued.increment();
      return recordId;
    } catch (JsonProcessingException error) {
      throw new IllegalArgumentException("Failed to serialize critical event", error);
    } catch (DataAccessException error) {
      healthy.set(false);
      failures.increment();
      throw error;
    }
  }

  public List<QueuedCriticalEvent> poll() {
    try {
      ensureGroup();
      List<MapRecord<String, String, String>> records =
          streamOperations()
              .read(
                  Consumer.from(properties.consumerGroup(), properties.consumerName()),
                  StreamReadOptions.empty().count(properties.batchSize()),
                  StreamOffset.create(properties.streamKey(), ReadOffset.lastConsumed()));
      healthy.set(true);
      if (records == null || records.isEmpty()) {
        return List.of();
      }
      pendingCount.addAndGet(records.size());
      return records.stream().map(record -> decode(record, false)).toList();
    } catch (RuntimeException error) {
      groupReady.set(false);
      healthy.set(false);
      failures.increment();
      throw error;
    }
  }

  public boolean isHealthy() {
    return healthy.get();
  }

  public long pendingCount() {
    return pendingCount.get();
  }

  private StreamOperations<String, String, String> streamOperations() {
    return redis.<String, String>opsForStream();
  }

  private QueuedCriticalEvent decode(MapRecord<String, String, String> record, boolean reclaimed) {
    String payload = record.getValue().get(PAYLOAD_FIELD);
    if (payload == null) {
      throw new IllegalStateException("Critical stream record has no payload: " + record.getId());
    }
    try {
      return new QueuedCriticalEvent(
          record.getId(), objectMapper.readValue(payload, CriticalEvent.class), reclaimed);
    } catch (JsonProcessingException error) {
      throw new IllegalStateException("Failed to deserialize critical event", error);
    }
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
    } catch (DataAccessException error) {
      Throwable cause = error;
      while (cause != null && !String.valueOf(cause.getMessage()).contains("BUSYGROUP")) {
        cause = cause.getCause();
      }
      if (cause == null) {
        throw error;
      }
    }
    groupReady.set(true);
  }
}
