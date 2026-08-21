package com.sportsbook.wallet.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

class KafkaOutboxDispatcherTest {

  @Test
  @SuppressWarnings("unchecked")
  void sendsTheKeyPayloadAndOneCanonicalEventIdHeader() {
    KafkaTemplate<String, byte[]> kafka = org.mockito.Mockito.mock(KafkaTemplate.class);
    when(kafka.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));
    KafkaOutboxDispatcher dispatcher = new KafkaOutboxDispatcher(kafka);
    LeasedOutboxMessage message = message();

    dispatcher.dispatch(message).toCompletableFuture().join();

    ArgumentCaptor<ProducerRecord<String, byte[]>> recordCaptor =
        ArgumentCaptor.forClass(ProducerRecord.class);
    verify(kafka).send(recordCaptor.capture());
    ProducerRecord<String, byte[]> record = recordCaptor.getValue();
    assertThat(record.topic()).isEqualTo(message.topic());
    assertThat(record.key()).isEqualTo(message.partitionKey());
    assertThat(record.value()).containsExactly(message.payload());
    assertThat(record.headers().toArray()).hasSize(1);
    assertThat(record.headers().lastHeader(KafkaOutboxDispatcher.EVENT_ID_HEADER).value())
        .asString(StandardCharsets.US_ASCII)
        .isEqualTo(message.lease().eventId().toString());
  }

  private LeasedOutboxMessage message() {
    UUID eventId = UUID.fromString("0198ca71-8000-7000-8000-0000000000af");
    Instant created = Instant.parse("2026-08-21T00:00:00Z");
    return new LeasedOutboxMessage(
        new OutboxLease(eventId, "worker-a", 1, created.plusSeconds(30)),
        "wallet.debited.v1",
        "bet-1",
        "WalletDebited",
        new byte[] {1, 2, 3},
        1L,
        false,
        1,
        created);
  }
}
