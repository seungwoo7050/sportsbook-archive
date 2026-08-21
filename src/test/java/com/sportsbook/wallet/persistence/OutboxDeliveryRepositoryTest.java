package com.sportsbook.wallet.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.wallet.outbox.OutboxAppender;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

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
}
