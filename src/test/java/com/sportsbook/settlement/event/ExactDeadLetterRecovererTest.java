package com.sportsbook.settlement.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaOperations;

class ExactDeadLetterRecovererTest {

  @SuppressWarnings("unchecked")
  private final KafkaOperations<byte[], byte[]> kafka = mock(KafkaOperations.class);

  @Test
  void preservesPartitionRawBytesAndApplicationHeaders() {
    when(kafka.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));
    byte[] key = "key".getBytes(StandardCharsets.UTF_8);
    byte[] value = {1, 2, 3};
    ConsumerRecord<byte[], byte[]> source =
        new ConsumerRecord<>("bet.placed.v1", 2, 19, key, value);
    source.headers().add("traceparent", "trace".getBytes(StandardCharsets.UTF_8));

    new ExactDeadLetterRecoverer(kafka, "settlement-placement", Duration.ofSeconds(11))
        .recover(source, new IllegalArgumentException("poison"));

    ArgumentCaptor<ProducerRecord<byte[], byte[]>> sent =
        ArgumentCaptor.forClass(ProducerRecord.class);
    verify(kafka).send(sent.capture());
    ProducerRecord<byte[], byte[]> record = sent.getValue();
    assertThat(record.topic()).isEqualTo("bet.placed.v1.DLT");
    assertThat(record.partition()).isEqualTo(2);
    assertThat(record.key()).isSameAs(key);
    assertThat(record.value()).isSameAs(value);
    assertThat(text(record, "traceparent")).isEqualTo("trace");
    assertThat(text(record, ExactDeadLetterRecoverer.ORIGINAL_TOPIC)).isEqualTo("bet.placed.v1");
    assertThat(number(record, ExactDeadLetterRecoverer.ORIGINAL_OFFSET)).isEqualTo(19L);
  }

  @Test
  void propagatesAnExactPartitionSendFailure() {
    CompletableFuture<org.springframework.kafka.support.SendResult<byte[], byte[]>> failed =
        new CompletableFuture<>();
    failed.completeExceptionally(new IllegalStateException("missing DLT partition"));
    when(kafka.send(any(ProducerRecord.class))).thenReturn(failed);
    ConsumerRecord<byte[], byte[]> source =
        new ConsumerRecord<>("match.result", 4, 1, new byte[0], new byte[0]);

    assertThatThrownBy(
            () ->
                new ExactDeadLetterRecoverer(kafka, "settlement-result", Duration.ofSeconds(1))
                    .recover(source, new IllegalArgumentException("poison")))
        .isInstanceOf(KafkaException.class)
        .hasMessageContaining("Failed to publish exact DLT");
  }

  @Test
  void republishesNullPayloadsToTheSameDeadLetterPartition() {
    when(kafka.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));
    ConsumerRecord<byte[], byte[]> tombstone =
        new ConsumerRecord<>("match.result.v1", 3, 7, new byte[0], null);

    new ExactDeadLetterRecoverer(kafka, "settlement-result", Duration.ofSeconds(1))
        .recover(tombstone, new StrictAvroDecoder.DecodeException("null payload"));

    ArgumentCaptor<ProducerRecord<byte[], byte[]>> sent =
        ArgumentCaptor.forClass(ProducerRecord.class);
    verify(kafka).send(sent.capture());
    assertThat(sent.getValue().partition()).isEqualTo(3);
    assertThat(sent.getValue().value()).isNull();
  }

  private static String text(ProducerRecord<byte[], byte[]> record, String name) {
    return new String(record.headers().lastHeader(name).value(), StandardCharsets.UTF_8);
  }

  private static long number(ProducerRecord<byte[], byte[]> record, String name) {
    return ByteBuffer.wrap(record.headers().lastHeader(name).value()).getLong();
  }
}
