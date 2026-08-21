package com.sportsbook.gateway.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.support.serializer.SerializationUtils;

class DltReplayRecordFactoryTest {

  private static final List<String> FRAMEWORK_HEADERS =
      List.of(
          KafkaHeaders.DLT_EXCEPTION_FQCN,
          KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN,
          KafkaHeaders.DLT_EXCEPTION_STACKTRACE,
          KafkaHeaders.DLT_EXCEPTION_MESSAGE,
          KafkaHeaders.DLT_KEY_EXCEPTION_FQCN,
          KafkaHeaders.DLT_KEY_EXCEPTION_STACKTRACE,
          KafkaHeaders.DLT_KEY_EXCEPTION_MESSAGE,
          KafkaHeaders.DLT_ORIGINAL_TOPIC,
          KafkaHeaders.DLT_ORIGINAL_PARTITION,
          KafkaHeaders.DLT_ORIGINAL_OFFSET,
          KafkaHeaders.DLT_ORIGINAL_CONSUMER_GROUP,
          KafkaHeaders.DLT_ORIGINAL_TIMESTAMP,
          KafkaHeaders.DLT_ORIGINAL_TIMESTAMP_TYPE,
          KafkaHeaders.EXCEPTION_FQCN,
          KafkaHeaders.EXCEPTION_CAUSE_FQCN,
          KafkaHeaders.EXCEPTION_STACKTRACE,
          KafkaHeaders.EXCEPTION_MESSAGE,
          KafkaHeaders.KEY_EXCEPTION_FQCN,
          KafkaHeaders.KEY_EXCEPTION_STACKTRACE,
          KafkaHeaders.KEY_EXCEPTION_MESSAGE,
          KafkaHeaders.ORIGINAL_TOPIC,
          KafkaHeaders.ORIGINAL_PARTITION,
          KafkaHeaders.ORIGINAL_OFFSET,
          KafkaHeaders.ORIGINAL_TIMESTAMP,
          KafkaHeaders.ORIGINAL_TIMESTAMP_TYPE,
          KafkaHeaders.DELIVERY_ATTEMPT,
          SerializationUtils.KEY_DESERIALIZER_EXCEPTION_HEADER,
          SerializationUtils.VALUE_DESERIALIZER_EXCEPTION_HEADER);

  @Test
  void preservesRawApplicationEvidenceAndRemovesFrameworkMetadata() {
    byte[] key = {(byte) 0xc3, 0x28};
    byte[] value = {0, (byte) 0xff, 7};
    byte[] first = {1, 2};
    byte[] prefixed = {3, 4};
    ConsumerRecord<byte[], byte[]> deadLetter =
        new ConsumerRecord<>("odds.changed.DLT", 3, 9L, key, value);
    deadLetter.headers().add("application", first);
    deadLetter.headers().add("application", null);
    deadLetter.headers().add("kafka_application", prefixed);
    FRAMEWORK_HEADERS.forEach(name -> deadLetter.headers().add(name, new byte[] {8}));

    DltReplayRecordFactory factory =
        new DltReplayRecordFactory(
            new GatewayTopicProperties(
                "odds.changed", "bet.settled.v1", "bet.voided.v1", "bet.resolution.revised.v1"));
    var replay = factory.replay(deadLetter);

    assertThat(replay.topic()).isEqualTo("odds.changed");
    assertThat(replay.partition()).isEqualTo(3);
    assertThat(replay.key()).containsExactly(key).isNotSameAs(key);
    assertThat(replay.value()).containsExactly(value).isNotSameAs(value);
    var application = replay.headers().headers("application").iterator();
    assertThat(application.next().value()).containsExactly(first).isNotSameAs(first);
    assertThat(application.next().value()).isNull();
    assertThat(application.hasNext()).isFalse();
    Header kafkaApplication = replay.headers().lastHeader("kafka_application");
    assertThat(kafkaApplication.value()).containsExactly(prefixed).isNotSameAs(prefixed);
    FRAMEWORK_HEADERS.forEach(name -> assertThat(replay.headers().lastHeader(name)).isNull());
    assertThatThrownBy(
            () -> factory.replay(new ConsumerRecord<>("odds.changed.DLT.DLT", 3, 9L, key, value)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
