package com.sportsbook.oddsfeed.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportsbook.oddsfeed.config.CriticalDeliveryProperties;
import com.sportsbook.protocol.event.EventLifecycleStatus;
import com.sportsbook.protocol.value.EventId;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class CriticalEventQueueTest {

  @Container
  private static final GenericContainer<?> REDIS =
      new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
  private LettuceConnectionFactory connectionFactory;
  private StringRedisTemplate redis;
  private String streamKey;
  private CriticalEventQueue queue;

  @BeforeEach
  void setUp() {
    connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getFirstMappedPort());
    connectionFactory.afterPropertiesSet();
    redis = new StringRedisTemplate(connectionFactory);
    redis.afterPropertiesSet();
    streamKey = "critical-events:" + UUID.randomUUID();
    queue = queue("publisher-1", Duration.ofSeconds(5));
  }

  @AfterEach
  void tearDown() {
    connectionFactory.destroy();
  }

  @Test
  void appendsSerializedEventsToTheConfiguredStream() throws Exception {
    CriticalEvent event = lifecycle(EventLifecycleStatus.SCHEDULED);

    RecordId recordId = queue.enqueue(event);

    List<MapRecord<String, String, String>> records =
        redis.<String, String>opsForStream().range(streamKey, Range.unbounded());
    assertThat(records).hasSize(1);
    assertThat(records.get(0).getId()).isEqualTo(recordId);
    assertThat(
            objectMapper.readValue(records.get(0).getValue().get("payload"), CriticalEvent.class))
        .isEqualTo(event);
    assertThat(queue.isHealthy()).isTrue();
  }

  @Test
  void consumesUnreadEventsInStreamOrder() {
    CriticalEvent first = lifecycle(EventLifecycleStatus.SCHEDULED);
    CriticalEvent second = lifecycle(EventLifecycleStatus.IN_PLAY);
    queue.enqueue(first);
    queue.enqueue(second);

    assertThat(queue.poll()).extracting(QueuedCriticalEvent::event).containsExactly(first, second);
    assertThat(queue.poll()).isEmpty();
  }

  @Test
  void reclaimsPendingEventsForAReplacementConsumer() {
    queue.enqueue(lifecycle(EventLifecycleStatus.SCHEDULED));
    QueuedCriticalEvent firstDelivery = queue.poll().get(0);

    CriticalEventQueue replacement = queue("publisher-2", Duration.ZERO);
    QueuedCriticalEvent recovered = replacement.poll().get(0);

    assertThat(firstDelivery.reclaimed()).isFalse();
    assertThat(recovered.reclaimed()).isTrue();
    assertThat(recovered.recordId()).isEqualTo(firstDelivery.recordId());
    assertThat(recovered.event()).isEqualTo(firstDelivery.event());
    assertThat(replacement.pendingCount()).isEqualTo(1);
  }

  @Test
  void acknowledgesAndDeletesCompletedRecords() {
    queue.enqueue(lifecycle(EventLifecycleStatus.SCHEDULED));
    QueuedCriticalEvent queued = queue.poll().get(0);

    queue.acknowledge(queued);

    assertThat(queue.pendingCount()).isZero();
    assertThat(redis.opsForStream().pending(streamKey, "publisher").getTotalPendingMessages())
        .isZero();
    assertThat(redis.opsForStream().size(streamKey)).isZero();
  }

  private CriticalEventQueue queue(String consumerName, Duration claimIdle) {
    return new CriticalEventQueue(
        redis,
        objectMapper,
        new CriticalDeliveryProperties(streamKey, "publisher", consumerName, 25, claimIdle),
        new SimpleMeterRegistry());
  }

  private static CriticalEvent lifecycle(EventLifecycleStatus status) {
    return CriticalEvent.lifecycle(
        new EventId(UUID.randomUUID()), status, Instant.EPOCH, Instant.EPOCH);
  }
}
