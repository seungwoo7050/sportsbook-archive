package com.sportsbook.betting.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;

class OutboxPublisherTest {

  @Test
  void recordsPublicationOnlyAfterKafkaAcknowledgement() {
    OutboxEventRepository repository = mock(OutboxEventRepository.class);
    @SuppressWarnings("unchecked")
    KafkaTemplate<String, byte[]> kafka = mock(KafkaTemplate.class);
    OutboxEvent event =
        OutboxEvent.pending(
            UUID.randomUUID(), "topic", "key", "Schema", new byte[] {1}, Instant.EPOCH);
    when(repository.findUnpublished(any(Pageable.class))).thenReturn(List.of(event));
    when(kafka.send(any(org.apache.kafka.clients.producer.ProducerRecord.class)))
        .thenReturn(CompletableFuture.completedFuture(null));
    Instant now = Instant.parse("2026-08-22T00:00:00Z");

    new OutboxPublisher(repository, kafka, Clock.fixed(now, ZoneOffset.UTC)).publishPending();

    assertThat(event.publishedAt()).isEqualTo(now);
  }

  @Test
  void usesTheIsolatedSchedulerAndBoundedDeliveryWait() throws Exception {
    Scheduled scheduled =
        OutboxPublisher.class.getMethod("publishPending").getAnnotation(Scheduled.class);

    assertThat(scheduled.scheduler()).isEqualTo("outboxTaskScheduler");
    assertThat(OutboxPublisher.TIMEOUT_SECONDS).isEqualTo(11);
  }
}
