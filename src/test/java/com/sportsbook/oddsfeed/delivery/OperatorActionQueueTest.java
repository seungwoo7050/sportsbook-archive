package com.sportsbook.oddsfeed.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.oddsfeed.config.CacheProperties;
import com.sportsbook.oddsfeed.config.OperatorDeliveryProperties;
import com.sportsbook.protocol.event.MarketStatus;
import com.sportsbook.protocol.value.EventId;
import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.MarketId;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class OperatorActionQueueTest {

  private static final String STREAM = "test:operator-actions";
  private static final Instant NOW = Instant.parse("2026-08-21T05:00:00Z");

  @Container
  private static final GenericContainer<?> REDIS =
      new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

  private LettuceConnectionFactory connectionFactory;
  private StringRedisTemplate redis;

  @BeforeEach
  void setUp() {
    connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getFirstMappedPort());
    connectionFactory.afterPropertiesSet();
    redis = new StringRedisTemplate(connectionFactory);
    redis.afterPropertiesSet();
    redis.getConnectionFactory().getConnection().serverCommands().flushDb();
  }

  @AfterEach
  void tearDown() {
    connectionFactory.destroy();
  }

  @Test
  void sameCanonicalRequestReplaysAcrossAReplacementActionId() {
    OperatorActionQueue queue = queue();
    EventId eventId = new EventId(UUID.randomUUID());
    MarketId marketId = new MarketId(UUID.randomUUID());
    UUID originalActionId = UUID.randomUUID();
    IdempotencyKey key = IdempotencyKey.of("stable-key");

    OperatorActionSubmission created =
        queue.submit(
            key, originalActionId, eventId, marketId, MarketStatus.SUSPENDED, " incident ", NOW);
    OperatorActionSubmission replay =
        queue.submit(
            key, UUID.randomUUID(), eventId, marketId, MarketStatus.SUSPENDED, "incident", NOW);

    assertThat(created.outcome()).isEqualTo(OperatorActionSubmission.Outcome.CREATED);
    assertThat(replay.outcome()).isEqualTo(OperatorActionSubmission.Outcome.REPLAYED);
    assertThat(replay.actionId()).isEqualTo(originalActionId);
    assertThat(replay.recordId()).isEqualTo(created.recordId());
    assertThat(redis.opsForStream().size(STREAM)).isOne();
  }

  @Test
  void changedRequestOrReusedActionIdReturnsConflict() {
    OperatorActionQueue queue = queue();
    EventId eventId = new EventId(UUID.randomUUID());
    MarketId marketId = new MarketId(UUID.randomUUID());
    UUID actionId = UUID.randomUUID();
    queue.submit(
        IdempotencyKey.of("stable-key"),
        actionId,
        eventId,
        marketId,
        MarketStatus.SUSPENDED,
        "incident",
        NOW);

    assertThatThrownBy(
            () ->
                queue.submit(
                    IdempotencyKey.of("stable-key"),
                    UUID.randomUUID(),
                    eventId,
                    marketId,
                    MarketStatus.SUSPENDED,
                    "different",
                    NOW))
        .isInstanceOf(IdempotencyConflictException.class);
    assertThatThrownBy(
            () ->
                queue.submit(
                    IdempotencyKey.of("another-key"),
                    actionId,
                    eventId,
                    marketId,
                    MarketStatus.SUSPENDED,
                    "incident",
                    NOW))
        .isInstanceOf(IdempotencyConflictException.class);
    assertThat(redis.opsForStream().size(STREAM)).isOne();
  }

  private OperatorActionQueue queue() {
    return new OperatorActionQueue(
        redis,
        new OperatorDeliveryProperties(STREAM, "group", "consumer", 20, Duration.ZERO, 10),
        new CacheProperties(Duration.ofHours(24)),
        new SimpleMeterRegistry());
  }
}
