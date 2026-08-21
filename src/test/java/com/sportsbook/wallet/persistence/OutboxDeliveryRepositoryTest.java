package com.sportsbook.wallet.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.wallet.outbox.OutboxAppender;
import com.sportsbook.wallet.outbox.OutboxPublisher;
import com.sportsbook.wallet.outbox.OutboxRetryPolicy;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

@Tag("wallet-semantic-gate")
@DataJpaTest(properties = "spring.test.database.replace=NONE")
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({OutboxAppender.class, OutboxDeliveryRepository.class, OutboxStreamLock.class})
class OutboxDeliveryRepositoryTest extends OutboxDeliveryRepositoryFixture {
  @Autowired OutboxDeliveryRepository delivery;

  @Test
  void claimsDisjointKeysAcrossWorkers() {
    Instant created = Instant.parse("2999-08-21T00:00:00Z");
    persist("operation-a", "key-a", "dedup-a1", created);
    persist("operation-b", "key-b", "dedup-b1", created.plusMillis(1));

    var first = delivery.claim("worker-a", 1, Duration.ofSeconds(30));
    var disjoint = delivery.claim("worker-b", 10, Duration.ofSeconds(30));

    assertThat(first.get(0).partitionKey()).isEqualTo("key-a");
    assertThat(first.get(0).streamSequence()).isEqualTo(1L);
    assertThat(first.get(0).leaseTakeover()).isFalse();
    assertThat(disjoint.get(0).partitionKey()).isEqualTo("key-b");
  }

  @Test
  void reclaimsAnExpiredHeadWithANewFencingVersion() {
    persist("operation-a", "key-a", "dedup-a1", Instant.parse("2026-08-21T00:00:00Z"));
    persist("operation-a2", "key-a", "dedup-a2", Instant.parse("2026-08-21T00:00:01Z"));
    var first = delivery.claim("worker-a", 1, Duration.ofSeconds(30)).get(0);
    jdbc.update(
        "UPDATE outbox_event SET lease_until=clock_timestamp()-interval '1 second' WHERE event_id=?",
        first.lease().eventId());

    var reclaimed = delivery.claim("worker-b", 10, Duration.ofSeconds(30));

    assertThat(reclaimed).hasSize(1);
    assertThat(reclaimed.get(0).lease().eventId()).isEqualTo(first.lease().eventId());
    assertThat(reclaimed.get(0).streamSequence()).isEqualTo(first.streamSequence());
    assertThat(reclaimed.get(0).leaseTakeover()).isTrue();
    assertThat(reclaimed.get(0).lease().version()).isEqualTo(first.lease().version() + 1);
  }

  @Test
  void rejectsASecondSemanticEventForOneOperation() {
    Instant created = Instant.parse("2026-08-21T00:00:00Z");
    persist("operation-unique", "key-a", "dedup-a1", created);

    assertThatThrownBy(
            () ->
                persist(
                    "operation-unique",
                    "wallet.credited.v1",
                    "key-a",
                    "dedup-a2",
                    created.plusMillis(1)))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }

  @Test
  void preservesStreamSequenceOrdering() {
    Instant createdLater = Instant.parse("2026-08-21T00:00:01Z");
    Instant createdEarlier = createdLater.minusSeconds(1L);
    persist("operation-a", "key-a", "dedup-a1", createdLater);
    persist("operation-a2", "key-a", "dedup-a2", createdEarlier);

    var head = delivery.claim("worker-a", 10, Duration.ofSeconds(30));
    var blockedSuccessor = delivery.claim("worker-b", 10, Duration.ofSeconds(30));

    assertThat(head)
        .singleElement()
        .satisfies(message -> assertThat(message.streamSequence()).isOne());
    assertThat(head.get(0).createdAt()).isEqualTo(createdLater);
    assertThat(blockedSuccessor).isEmpty();
  }

  @Test
  void retriesRemainPendingAndFenceAnExpiredOwner() {
    Instant created = Instant.parse("2999-08-21T00:00:00Z");
    persist("operation-a", "key-a", "dedup-a1", created);
    persist("operation-a2", "key-a", "dedup-a2", created.plusSeconds(1L));
    var stale = delivery.claim("worker-a", 1, Duration.ofSeconds(30)).get(0);
    jdbc.update(
        "UPDATE outbox_event SET lease_until=clock_timestamp()-interval '1 second' WHERE event_id=?",
        stale.lease().eventId());
    var active = delivery.claim("worker-b", 1, Duration.ofSeconds(30)).get(0);

    assertThat(delivery.releaseForRetry(stale.lease(), Duration.ZERO, "stale")).isFalse();
    assertThat(delivery.releaseForRetry(active.lease(), Duration.ZERO, "retry")).isTrue();
    assertThat(
            jdbc.queryForObject(
                "SELECT available_at < created_at FROM outbox_event WHERE event_id=?",
                Boolean.class,
                stale.lease().eventId()))
        .isTrue();

    for (int attempt = 0; attempt < 50; attempt++) {
      var retry = delivery.claim("worker-b", 1, Duration.ofSeconds(30)).get(0);
      assertThat(retry.streamSequence()).isEqualTo(1L);
      assertThat(delivery.releaseForRetry(retry.lease(), Duration.ZERO, "retry")).isTrue();
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT attempt_count FROM outbox_event WHERE event_id=?",
                Integer.class,
                stale.lease().eventId()))
        .isEqualTo(52);
    assertThat(
            jdbc.queryForObject(
                "SELECT published_at IS NULL FROM outbox_event WHERE event_id=?",
                Boolean.class,
                stale.lease().eventId()))
        .isTrue();
    var finalHead = delivery.claim("worker-b", 1, Duration.ofSeconds(30)).get(0);
    assertThat(delivery.markPublished(finalHead.lease())).isTrue();
    assertThat(
            jdbc.queryForObject(
                "SELECT published_at < created_at FROM outbox_event WHERE event_id=?",
                Boolean.class,
                stale.lease().eventId()))
        .isTrue();
    assertThat(delivery.claim("worker-b", 1, Duration.ofSeconds(30)).get(0).streamSequence())
        .isEqualTo(2L);
  }

  @Test
  void snapshotsPendingLeasedAndDatabaseClockAge() {
    Instant created = Instant.parse("2026-08-21T00:00:00Z");
    persist("operation-a", "key-a", "dedup-a1", created);
    persist("operation-b", "key-b", "dedup-b1", created.plusMillis(1));
    var leased = delivery.claim("worker-a", 1, Duration.ofSeconds(30)).get(0);
    jdbc.update(
        """
        UPDATE outbox_event
        SET created_at=clock_timestamp()-interval '10 seconds',
            available_at=clock_timestamp()-interval '10 seconds'
        WHERE published_at IS NULL
        """);

    var active = delivery.snapshot();

    assertThat(active.pending()).isEqualTo(2);
    assertThat(active.leased()).isEqualTo(1);
    assertThat(active.oldestPendingSeconds()).isGreaterThanOrEqualTo(9.0);
    assertThat(delivery.markPublished(leased.lease())).isTrue();
    var afterPublish = delivery.snapshot();
    assertThat(afterPublish.pending()).isEqualTo(1);
    assertThat(afterPublish.leased()).isZero();
    assertThat(afterPublish.oldestPendingSeconds()).isGreaterThanOrEqualTo(9.0);
  }

  @Test
  void twoPublishersConvergeAfterTheFirstWorkerIsLost() {
    Instant created = Instant.parse("2026-08-21T00:00:00Z");
    persist("operation-a", "key-a", "dedup-a1", created);
    persist("operation-a2", "key-a", "dedup-a2", created.plusMillis(1));
    CompletableFuture<Void> abandonedSend = new CompletableFuture<>();
    java.util.List<com.sportsbook.wallet.outbox.LeasedOutboxMessage> recovered =
        new java.util.concurrent.CopyOnWriteArrayList<>();
    OutboxRetryPolicy policy = new OutboxRetryPolicy(Duration.ofMillis(1), Duration.ofSeconds(1));
    OutboxPublisher workerA =
        new OutboxPublisher(
            delivery,
            ignored -> abandonedSend,
            policy,
            Runnable::run,
            "worker-a",
            1,
            1,
            Duration.ofSeconds(30));
    OutboxPublisher workerB =
        new OutboxPublisher(
            delivery,
            message -> {
              recovered.add(message);
              return CompletableFuture.completedFuture(null);
            },
            policy,
            Runnable::run,
            "worker-b",
            1,
            1,
            Duration.ofSeconds(30));

    workerA.poll();
    jdbc.update(
        "UPDATE outbox_event SET lease_until=clock_timestamp()-interval '1 second' WHERE lease_owner='worker-a'");
    workerB.poll();
    abandonedSend.complete(null);
    workerB.poll();

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM outbox_event WHERE published_at IS NOT NULL", Integer.class))
        .isEqualTo(2);
    assertThat(jdbc.queryForObject("SELECT sum(attempt_count) FROM outbox_event", Integer.class))
        .isEqualTo(3);
    assertThat(recovered)
        .extracting(message -> message.leaseTakeover())
        .containsExactly(true, false);
    assertThat(recovered).extracting(message -> message.streamSequence()).containsExactly(1L, 2L);
  }
}
