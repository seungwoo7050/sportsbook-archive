package com.sportsbook.settlement.outbox;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sportsbook.settlement.config.SettlementRuntimeProperties;
import com.sportsbook.settlement.config.SettlementWorkerConfiguration;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.scheduling.annotation.Scheduled;

class OutboxPublisherTest {

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
  private final OutboxEventRepository repository = mock(OutboxEventRepository.class);

  @SuppressWarnings("unchecked")
  private final KafkaOperations<byte[], byte[]> kafka = mock(KafkaOperations.class);

  private final OutboxPublisher publisher =
      new OutboxPublisher(
          repository,
          kafka,
          new SettlementRuntimeProperties(null, null, null, 10),
          Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void marksRowsOnlyAfterTheRawBrokerSendCompletes() {
    OutboxEvent event = pending();
    when(repository.lockNextUnpublished(10)).thenReturn(List.of(event));
    when(kafka.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));

    assertThat(publisher.publishBatch()).isOne();

    ArgumentCaptor<ProducerRecord<byte[], byte[]>> sent =
        ArgumentCaptor.forClass(ProducerRecord.class);
    verify(kafka).send(sent.capture());
    assertThat(new String(sent.getValue().key(), UTF_8)).isEqualTo("event-id");
    assertThat(sent.getValue().value()).containsExactly(1, 2, 3);
    assertThat(event.publishedAt()).isEqualTo(NOW);
  }

  @Test
  void leavesTheRowPendingWhenTheBrokerSendFails() {
    OutboxEvent event = pending();
    when(repository.lockNextUnpublished(10)).thenReturn(List.of(event));
    CompletableFuture<org.springframework.kafka.support.SendResult<byte[], byte[]>> failed =
        new CompletableFuture<>();
    failed.completeExceptionally(new IllegalStateException("broker unavailable"));
    when(kafka.send(any(ProducerRecord.class))).thenReturn(failed);

    assertThatThrownBy(publisher::publishBatch).isInstanceOf(KafkaException.class);
    assertThat(event.publishedAt()).isNull();
  }

  @Test
  void runsOnTheIsolatedOutboxScheduler() throws NoSuchMethodException {
    Scheduled scheduled =
        OutboxPublisher.class.getMethod("publishBatch").getAnnotation(Scheduled.class);

    assertThat(scheduled.scheduler()).isEqualTo(SettlementWorkerConfiguration.OUTBOX);
  }

  private static OutboxEvent pending() {
    return OutboxEvent.pending(
        "bet.settled.v1", "event-id", "BetSettled", new byte[] {1, 2, 3}, Instant.EPOCH);
  }
}
