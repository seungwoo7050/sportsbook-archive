package com.sportsbook.gateway.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

@SpringBootTest(
    properties = {
      "spring.main.web-application-type=none",
      "management.tracing.enabled=false",
      "logging.level.kafka=ERROR",
      "logging.level.org.apache.kafka=ERROR",
      "logging.level.org.springframework.kafka=ERROR"
    })
@EmbeddedKafka(
    partitions = 1,
    topics = {"odds.changed", "odds.changed.DLT"},
    bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class GatewayDeadLetterRawRecordTest {

  @Autowired private EmbeddedKafkaBroker broker;
  @Autowired private DeadLetterPublishingRecoverer recoverer;

  @Test
  void retainsRawEvidenceAndAddsRecoveryMetadata() {
    byte[] malformedUtf8Key = {(byte) 0xc3, (byte) 0x28};
    byte[] payload = {0, (byte) 0xff, 3, 7};
    String sourceGroup = "gateway-odds";
    Map<String, Object> properties =
        KafkaTestUtils.consumerProps("raw-dlt-" + UUID.randomUUID(), "false", broker);

    try (Consumer<byte[], byte[]> consumer =
        new DefaultKafkaConsumerFactory<>(
                properties, new ByteArrayDeserializer(), new ByteArrayDeserializer())
            .createConsumer()) {
      consumer.subscribe(java.util.List.of("odds.changed.DLT"));
      ConsumerRecord<byte[], byte[]> source =
          new ConsumerRecord<>("odds.changed", 0, 7L, malformedUtf8Key, payload);
      source.headers().add("kafka_application", new byte[] {1, 2});
      source.headers().add("kafka_application", null);

      recoverer.accept(
          source,
          new ListenerExecutionFailedException(
              "failed event", sourceGroup, new IllegalStateException("contract failure")));

      ConsumerRecord<byte[], byte[]> failed =
          KafkaTestUtils.getSingleRecord(consumer, "odds.changed.DLT", Duration.ofSeconds(10));
      assertThat(failed.key()).containsExactly(malformedUtf8Key);
      assertThat(failed.value()).containsExactly(payload);
      var applicationHeaders = failed.headers().headers("kafka_application").iterator();
      assertThat(applicationHeaders.next().value()).containsExactly(1, 2);
      assertThat(applicationHeaders.next().value()).isNull();
      assertThat(applicationHeaders.hasNext()).isFalse();
      assertThat(failed.headers().toArray())
          .extracting(Header::key)
          .contains(
              KafkaHeaders.DLT_ORIGINAL_TOPIC,
              KafkaHeaders.DLT_ORIGINAL_PARTITION,
              KafkaHeaders.DLT_ORIGINAL_OFFSET,
              KafkaHeaders.DLT_ORIGINAL_CONSUMER_GROUP,
              KafkaHeaders.DLT_EXCEPTION_FQCN);
      assertThat(
              new String(
                  failed.headers().lastHeader(KafkaHeaders.DLT_ORIGINAL_CONSUMER_GROUP).value(),
                  StandardCharsets.UTF_8))
          .isEqualTo(sourceGroup);
    }
  }
}
