package com.sportsbook.betting.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.UnknownTopicOrPartitionException;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.SendResult;

@SuppressWarnings("unchecked")
class KafkaRecoveryConfigTest {

  @Test
  void preservesRawKeysAndValuesForDltPublication() {
    Map<String, Object> properties = KafkaRecoveryConfig.rawProducerProperties("broker:9092");

    assertThat(properties.get(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG))
        .isEqualTo(ByteArraySerializer.class);
    assertThat(properties.get(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG))
        .isEqualTo(ByteArraySerializer.class);
    assertThat(properties.get(ProducerConfig.ACKS_CONFIG)).isEqualTo("all");
    assertThat(properties.get(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG)).isEqualTo(true);
    assertThat(properties.get(ProducerConfig.MAX_BLOCK_MS_CONFIG)).isEqualTo(5_000);
    assertThat(properties.get(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG)).isEqualTo(5_000);
    assertThat(properties.get(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG)).isEqualTo(10_000);
  }

  @Test
  void publishesPermanentRecordsToTheSamePartitionWithRawBytes() {
    KafkaTemplate<byte[], byte[]> kafka = successfulKafka();
    byte[] key = {1, 2};
    byte[] value = {3, 4};
    ConsumerRecord<byte[], byte[]> source =
        new ConsumerRecord<>("wallet.debited.v1", 3, 7, key, value);

    boolean recovered =
        KafkaRecoveryConfig.errorHandler(kafka, 0L)
            .handleOne(
                new PermanentKafkaException("bad record"),
                source,
                mock(Consumer.class),
                mock(MessageListenerContainer.class));

    ArgumentCaptor<ProducerRecord<byte[], byte[]>> sent =
        ArgumentCaptor.forClass(ProducerRecord.class);
    verify(kafka).send(sent.capture());
    assertThat(recovered).isTrue();
    assertThat(sent.getValue().topic()).isEqualTo("wallet.debited.v1.DLT");
    assertThat(sent.getValue().partition()).isEqualTo(3);
    assertThat(sent.getValue().key()).containsExactly(key);
    assertThat(sent.getValue().value()).containsExactly(value);
    verify(kafka, never()).partitionsFor(anyString());
  }

  @Test
  void leavesTransientAndFailedDltPublicationUnrecovered() {
    ConsumerRecord<byte[], byte[]> source =
        new ConsumerRecord<>("bet.settled.v1", 1, 9, new byte[] {1}, new byte[] {2});
    KafkaTemplate<byte[], byte[]> transientKafka = successfulKafka();
    Consumer<byte[], byte[]> transientConsumer = mock(Consumer.class);

    boolean transientRecovered =
        KafkaRecoveryConfig.errorHandler(transientKafka, 0L)
            .handleOne(
                new RuntimeException("database unavailable"),
                source,
                transientConsumer,
                mock(MessageListenerContainer.class));

    assertThat(transientRecovered).isFalse();
    verify(transientKafka, never()).send(any(ProducerRecord.class));
    verifyNoInteractions(transientConsumer);

    KafkaTemplate<byte[], byte[]> failedKafka =
        kafka(
            CompletableFuture.failedFuture(
                new UnknownTopicOrPartitionException("missing DLT partition")));
    Consumer<byte[], byte[]> failedConsumer = mock(Consumer.class);
    org.springframework.kafka.listener.DefaultErrorHandler handler =
        KafkaRecoveryConfig.errorHandler(failedKafka, 0L);
    boolean failedRecovered =
        handler.handleOne(
            new PermanentKafkaException("bad record"),
            source,
            failedConsumer,
            mock(MessageListenerContainer.class));
    assertThat(failedRecovered).isFalse();
    assertThat(handler.isAckAfterHandle()).isTrue();
    verify(failedKafka).send(any(ProducerRecord.class));
    verifyNoInteractions(failedConsumer);
  }

  private static KafkaTemplate<byte[], byte[]> successfulKafka() {
    SendResult<byte[], byte[]> result = mock(SendResult.class);
    return kafka(CompletableFuture.completedFuture(result));
  }

  private static KafkaTemplate<byte[], byte[]> kafka(
      CompletableFuture<SendResult<byte[], byte[]>> result) {
    KafkaTemplate<byte[], byte[]> kafka = mock(KafkaTemplate.class);
    ProducerFactory<byte[], byte[]> factory = mock(ProducerFactory.class);
    when(kafka.getProducerFactory()).thenReturn(factory);
    when(factory.getConfigurationProperties())
        .thenReturn(Map.of(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 5_000));
    when(kafka.send(any(ProducerRecord.class))).thenReturn(result);
    return kafka;
  }
}
