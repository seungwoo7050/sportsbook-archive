package com.sportsbook.oddsfeed.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.oddsfeed.cache.CacheKeys;
import com.sportsbook.oddsfeed.config.CacheProperties;
import com.sportsbook.oddsfeed.config.OperatorDeliveryProperties;
import com.sportsbook.protocol.event.MarketStatus;
import com.sportsbook.protocol.value.EventId;
import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.MarketId;
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

  @Test
  void restrictiveSubmissionProjectsClosedStateBeforeReturn() {
    OperatorActionQueue queue = queue();
    EventId eventId = new EventId(UUID.randomUUID());
    MarketId marketId = new MarketId(UUID.randomUUID());
    redis.opsForValue().set(CacheKeys.market(eventId, marketId), MarketStatus.OPEN.name());

    queue.submit(
        IdempotencyKey.of("close-key"),
        UUID.randomUUID(),
        eventId,
        marketId,
        MarketStatus.CLOSED,
        " incident ",
        NOW);

    assertThat(redis.opsForValue().get(CacheKeys.marketOverride(eventId, marketId)))
        .isEqualTo(MarketStatus.CLOSED.name());
    assertThat(redis.opsForValue().get(CacheKeys.market(eventId, marketId)))
        .isEqualTo(MarketStatus.CLOSED.name());
    assertThat(redis.opsForHash().get(CacheKeys.eventMarkets(eventId), marketId.value().toString()))
        .isEqualTo(MarketStatus.CLOSED.name());
    assertThat(redis.opsForStream().size(STREAM)).isOne();
  }

  @Test
  void terminalRestrictionRemainsEffectivelyClosed() {
    OperatorActionQueue queue = queue();
    EventId eventId = new EventId(UUID.randomUUID());
    MarketId marketId = new MarketId(UUID.randomUUID());
    redis.opsForValue().set(CacheKeys.eventTerminal(eventId), "FINISHED");

    queue.submit(
        IdempotencyKey.of("terminal-key"),
        UUID.randomUUID(),
        eventId,
        marketId,
        MarketStatus.SUSPENDED,
        "late suspension",
        NOW);

    assertThat(redis.opsForValue().get(CacheKeys.marketOverride(eventId, marketId)))
        .isEqualTo(MarketStatus.SUSPENDED.name());
    assertThat(redis.opsForValue().get(CacheKeys.market(eventId, marketId)))
        .isEqualTo(MarketStatus.CLOSED.name());
    assertThat(redis.opsForHash().get(CacheKeys.eventMarkets(eventId), marketId.value().toString()))
        .isEqualTo(MarketStatus.CLOSED.name());
  }

  @Test
  void terminalEventOrMarketRejectsReopenWithoutEnqueueing() {
    OperatorActionQueue queue = queue();
    EventId eventId = new EventId(UUID.randomUUID());
    MarketId marketId = new MarketId(UUID.randomUUID());
    redis.opsForValue().set(CacheKeys.eventTerminal(eventId), "FINISHED");

    assertThatThrownBy(
            () ->
                queue.submit(
                    IdempotencyKey.of("event-terminal"),
                    UUID.randomUUID(),
                    eventId,
                    marketId,
                    MarketStatus.OPEN,
                    "review",
                    NOW))
        .isInstanceOf(TerminalMarketReopenException.class);

    redis.delete(CacheKeys.eventTerminal(eventId));
    redis.opsForValue().set(CacheKeys.marketTerminal(eventId, marketId), "MARKET_CLOSED");
    assertThatThrownBy(
            () ->
                queue.submit(
                    IdempotencyKey.of("market-terminal"),
                    UUID.randomUUID(),
                    eventId,
                    marketId,
                    MarketStatus.OPEN,
                    "review",
                    NOW))
        .isInstanceOf(TerminalMarketReopenException.class);
    assertThat(redis.hasKey(STREAM)).isFalse();
  }

  @Test
  void reopenRetainsOverrideUntilAcknowledgedDelivery() {
    OperatorActionQueue queue = queue();
    EventId eventId = new EventId(UUID.randomUUID());
    MarketId marketId = new MarketId(UUID.randomUUID());
    redis.opsForValue().set(CacheKeys.providerMarket(eventId, marketId), MarketStatus.OPEN.name());
    redis
        .opsForValue()
        .set(CacheKeys.marketOverride(eventId, marketId), MarketStatus.SUSPENDED.name());
    redis.opsForValue().set(CacheKeys.market(eventId, marketId), MarketStatus.SUSPENDED.name());
    redis
        .opsForValue()
        .set(CacheKeys.marketFeedHold(eventId, marketId), Long.toString(NOW.toEpochMilli()));

    queue.submit(
        IdempotencyKey.of("reopen-key"),
        UUID.randomUUID(),
        eventId,
        marketId,
        MarketStatus.OPEN,
        "review complete",
        NOW);

    assertThat(redis.opsForValue().get(CacheKeys.marketOverride(eventId, marketId)))
        .isEqualTo(MarketStatus.SUSPENDED.name());
    assertThat(redis.opsForValue().get(CacheKeys.market(eventId, marketId)))
        .isEqualTo(MarketStatus.SUSPENDED.name());
    var record = redis.opsForStream().range(STREAM, Range.unbounded()).get(0);
    assertThat(record.getValue()).containsEntry("announcedStatus", MarketStatus.SUSPENDED.name());
  }

  @Test
  void actionsReceiveMonotonicPerMarketPredecessors() {
    OperatorActionQueue queue = queue();
    EventId eventId = new EventId(UUID.randomUUID());
    MarketId marketId = new MarketId(UUID.randomUUID());

    OperatorActionSubmission first =
        queue.submit(
            IdempotencyKey.of("first-key"),
            UUID.randomUUID(),
            eventId,
            marketId,
            MarketStatus.SUSPENDED,
            "first",
            NOW);
    OperatorActionSubmission second =
        queue.submit(
            IdempotencyKey.of("second-key"),
            UUID.randomUUID(),
            eventId,
            marketId,
            MarketStatus.CLOSED,
            "second",
            NOW);

    assertThat(first.sequence()).isEqualTo(1);
    assertThat(first.predecessor()).isZero();
    assertThat(second.sequence()).isEqualTo(2);
    assertThat(second.predecessor()).isEqualTo(first.sequence());
    assertThat(redis.opsForValue().get(OperatorActionQueue.sequenceKey(eventId, marketId)))
        .isEqualTo("2");
    assertThat(redis.getExpire(OperatorActionQueue.sequenceKey(eventId, marketId))).isEqualTo(-1);
  }

  @Test
  void pollDecodesAndAtomicallyCleansUpNewActions() {
    OperatorActionQueue queue = queue();
    EventId eventId = new EventId(UUID.randomUUID());
    MarketId marketId = new MarketId(UUID.randomUUID());
    UUID actionId = UUID.randomUUID();
    queue.submit(
        IdempotencyKey.of("poll-key"),
        actionId,
        eventId,
        marketId,
        MarketStatus.SUSPENDED,
        "incident",
        NOW);

    QueuedOperatorMarketAction queued = queue.poll().get(0);

    assertThat(queued.reclaimed()).isFalse();
    assertThat(queued.action().actionId()).isEqualTo(actionId);
    assertThat(queued.action().eventId()).isEqualTo(eventId);
    assertThat(queued.action().marketId()).isEqualTo(marketId);
    assertThat(queued.action().announcedStatus()).isEqualTo(MarketStatus.SUSPENDED);
    assertThat(queued.action().sequence()).isEqualTo(1);
    assertThat(queued.action().predecessor()).isZero();
    queue.cleanup(queued);
    assertThat(redis.opsForStream().size(STREAM)).isZero();
    assertThat(redis.opsForStream().pending(STREAM, "group").getTotalPendingMessages()).isZero();
  }

  @Test
  void replacementConsumerReclaimsAnInterruptedDelivery() {
    OperatorActionQueue original = queue("before-crash", Duration.ZERO);
    EventId eventId = new EventId(UUID.randomUUID());
    MarketId marketId = new MarketId(UUID.randomUUID());
    original.submit(
        IdempotencyKey.of("pending-key"),
        UUID.randomUUID(),
        eventId,
        marketId,
        MarketStatus.SUSPENDED,
        "incident",
        NOW);
    QueuedOperatorMarketAction delivered = original.poll().get(0);

    QueuedOperatorMarketAction reclaimed = queue("after-restart", Duration.ZERO).poll().get(0);

    assertThat(reclaimed.action()).isEqualTo(delivered.action());
    assertThat(reclaimed.reclaimed()).isTrue();
  }

  @Test
  void completionAdvancesPredecessorsAndExpiresFinishedMappings() {
    OperatorActionQueue queue = queue();
    EventId eventId = new EventId(UUID.randomUUID());
    MarketId marketId = new MarketId(UUID.randomUUID());
    UUID firstActionId = UUID.randomUUID();
    IdempotencyKey firstKey = IdempotencyKey.of("completed-first");
    queue.submit(firstKey, firstActionId, eventId, marketId, MarketStatus.SUSPENDED, "first", NOW);
    queue.submit(
        IdempotencyKey.of("completed-second"),
        UUID.randomUUID(),
        eventId,
        marketId,
        MarketStatus.CLOSED,
        "second",
        NOW);
    List<QueuedOperatorMarketAction> actions = queue.poll();

    assertThat(queue.deliveryState(actions.get(1).action()))
        .isEqualTo(OperatorActionQueue.DeliveryState.BLOCKED);
    assertThat(queue.complete(actions.get(0).action()))
        .isEqualTo(OperatorActionQueue.Completion.SUPERSEDED);
    assertThat(redis.getExpire(OperatorActionQueue.sequenceKey(eventId, marketId))).isEqualTo(-1);
    assertThat(redis.getExpire(OperatorActionQueue.committedKey(eventId, marketId))).isEqualTo(-1);
    assertThat(queue.deliveryState(actions.get(1).action()))
        .isEqualTo(OperatorActionQueue.DeliveryState.READY);
    assertThat(queue.complete(actions.get(1).action()))
        .isEqualTo(OperatorActionQueue.Completion.APPLIED);

    assertThat(redis.getExpire(OperatorActionQueue.idempotencyRedisKey(firstKey)))
        .isBetween(Duration.ofDays(6).toSeconds(), Duration.ofDays(7).toSeconds());
    assertThat(redis.getExpire("oddsfeed:operator:action:" + firstActionId))
        .isBetween(Duration.ofDays(6).toSeconds(), Duration.ofDays(7).toSeconds());
    assertThat(redis.getExpire(OperatorActionQueue.sequenceKey(eventId, marketId)))
        .isBetween(Duration.ofDays(6).toSeconds(), Duration.ofDays(7).toSeconds());
    assertThat(redis.getExpire(OperatorActionQueue.committedKey(eventId, marketId)))
        .isBetween(Duration.ofDays(6).toSeconds(), Duration.ofDays(7).toSeconds());
    assertThat(redis.opsForValue().get(CacheKeys.market(eventId, marketId)))
        .isEqualTo(MarketStatus.CLOSED.name());
  }

  @Test
  void completionSurvivesACrashBeforeStreamAcknowledgement() {
    OperatorActionQueue original = queue("before-crash", Duration.ZERO);
    EventId eventId = new EventId(UUID.randomUUID());
    MarketId marketId = new MarketId(UUID.randomUUID());
    original.submit(
        IdempotencyKey.of("crash-key"),
        UUID.randomUUID(),
        eventId,
        marketId,
        MarketStatus.SUSPENDED,
        "incident",
        NOW);
    QueuedOperatorMarketAction delivered = original.poll().get(0);
    original.complete(delivered.action());

    OperatorActionQueue replacement = queue("after-restart", Duration.ZERO);
    QueuedOperatorMarketAction reclaimed = replacement.poll().get(0);
    assertThat(replacement.deliveryState(reclaimed.action()))
        .isEqualTo(OperatorActionQueue.DeliveryState.COMPLETED);
    replacement.cleanup(reclaimed);
    assertThat(redis.opsForStream().size(STREAM)).isZero();
  }

  @Test
  void newPendingActionPersistsSequenceStateBeforeItsExpiry() {
    OperatorActionQueue queue = queue();
    EventId eventId = new EventId(UUID.randomUUID());
    MarketId marketId = new MarketId(UUID.randomUUID());
    queue.submit(
        IdempotencyKey.of("completed-before-new"),
        UUID.randomUUID(),
        eventId,
        marketId,
        MarketStatus.SUSPENDED,
        "incident",
        NOW);
    QueuedOperatorMarketAction first = queue.poll().get(0);
    queue.complete(first.action());
    queue.cleanup(first);

    assertThat(redis.getExpire(OperatorActionQueue.sequenceKey(eventId, marketId))).isPositive();
    assertThat(redis.getExpire(OperatorActionQueue.committedKey(eventId, marketId))).isPositive();

    OperatorActionSubmission second =
        queue.submit(
            IdempotencyKey.of("new-before-expiry"),
            UUID.randomUUID(),
            eventId,
            marketId,
            MarketStatus.CLOSED,
            "escalated incident",
            NOW);

    assertThat(second.sequence()).isEqualTo(2);
    assertThat(redis.getExpire(OperatorActionQueue.sequenceKey(eventId, marketId))).isEqualTo(-1);
    assertThat(redis.getExpire(OperatorActionQueue.committedKey(eventId, marketId))).isEqualTo(-1);
  }

  @Test
  void deliveryDecisionSkipsAReopenAfterATerminalLatch() {
    OperatorActionQueue queue = queue();
    EventId eventId = new EventId(UUID.randomUUID());
    MarketId marketId = new MarketId(UUID.randomUUID());
    redis.opsForValue().set(CacheKeys.providerMarket(eventId, marketId), MarketStatus.OPEN.name());
    redis
        .opsForValue()
        .set(CacheKeys.marketOverride(eventId, marketId), MarketStatus.SUSPENDED.name());
    queue.submit(
        IdempotencyKey.of("terminal-after-submit"),
        UUID.randomUUID(),
        eventId,
        marketId,
        MarketStatus.OPEN,
        "review complete",
        NOW);
    QueuedOperatorMarketAction reopen = queue.poll().get(0);

    redis.opsForValue().set(CacheKeys.marketTerminal(eventId, marketId), "MARKET_CLOSED");

    assertThat(queue.deliveryDecision(reopen.action()).outcome())
        .isEqualTo(OperatorDeliveryDecision.Outcome.SKIP);
    assertThat(queue.complete(reopen.action())).isEqualTo(OperatorActionQueue.Completion.APPLIED);
    assertThat(redis.opsForValue().get(CacheKeys.market(eventId, marketId)))
        .isEqualTo(MarketStatus.CLOSED.name());
    assertThat(redis.hasKey(CacheKeys.marketOverride(eventId, marketId))).isTrue();
  }

  @Test
  void deliveryDecisionSkipsAReopenSupersededByANewerClose() {
    OperatorActionQueue queue = queue();
    EventId eventId = new EventId(UUID.randomUUID());
    MarketId marketId = new MarketId(UUID.randomUUID());
    redis.opsForValue().set(CacheKeys.providerMarket(eventId, marketId), MarketStatus.OPEN.name());
    redis
        .opsForValue()
        .set(CacheKeys.marketOverride(eventId, marketId), MarketStatus.SUSPENDED.name());
    queue.submit(
        IdempotencyKey.of("superseded-reopen"),
        UUID.randomUUID(),
        eventId,
        marketId,
        MarketStatus.OPEN,
        "review complete",
        NOW);
    queue.submit(
        IdempotencyKey.of("newer-close"),
        UUID.randomUUID(),
        eventId,
        marketId,
        MarketStatus.CLOSED,
        "incident returned",
        NOW);
    List<QueuedOperatorMarketAction> actions = queue.poll();

    assertThat(queue.deliveryDecision(actions.get(0).action()).outcome())
        .isEqualTo(OperatorDeliveryDecision.Outcome.SKIP);
    assertThat(queue.complete(actions.get(0).action()))
        .isEqualTo(OperatorActionQueue.Completion.SUPERSEDED);
    assertThat(queue.deliveryDecision(actions.get(1).action()).announcedStatus())
        .isEqualTo(MarketStatus.CLOSED);
  }

  @Test
  void deliveryDecisionPublishesANormalReopenAsOpen() {
    assertThat(reopenDeliveryStatus(MarketStatus.OPEN, false)).isEqualTo(MarketStatus.OPEN);
  }

  @Test
  void deliveryDecisionReevaluatesProviderSuspension() {
    assertThat(reopenDeliveryStatus(MarketStatus.SUSPENDED, false))
        .isEqualTo(MarketStatus.SUSPENDED);
  }

  @Test
  void deliveryDecisionReevaluatesANewFeedHold() {
    assertThat(reopenDeliveryStatus(MarketStatus.OPEN, true)).isEqualTo(MarketStatus.SUSPENDED);
  }

  private MarketStatus reopenDeliveryStatus(MarketStatus providerAfterSubmit, boolean hold) {
    OperatorActionQueue queue = queue();
    EventId eventId = new EventId(UUID.randomUUID());
    MarketId marketId = new MarketId(UUID.randomUUID());
    redis.opsForValue().set(CacheKeys.providerMarket(eventId, marketId), MarketStatus.OPEN.name());
    redis
        .opsForValue()
        .set(CacheKeys.marketOverride(eventId, marketId), MarketStatus.SUSPENDED.name());
    queue.submit(
        IdempotencyKey.of("delivery-" + UUID.randomUUID()),
        UUID.randomUUID(),
        eventId,
        marketId,
        MarketStatus.OPEN,
        "review complete",
        NOW);
    QueuedOperatorMarketAction reopen = queue.poll().get(0);
    redis
        .opsForValue()
        .set(CacheKeys.providerMarket(eventId, marketId), providerAfterSubmit.name());
    if (hold) {
      redis
          .opsForValue()
          .set(CacheKeys.marketFeedHold(eventId, marketId), Long.toString(NOW.toEpochMilli()));
    }
    return queue.deliveryDecision(reopen.action()).announcedStatus();
  }

  private OperatorActionQueue queue() {
    return queue("consumer", Duration.ZERO);
  }

  private OperatorActionQueue queue(String consumer, Duration claimIdle) {
    return new OperatorActionQueue(
        redis,
        new OperatorDeliveryProperties(STREAM, "group", consumer, 20, claimIdle, 10),
        new CacheProperties(Duration.ofHours(24)),
        new SimpleMeterRegistry());
  }
}
