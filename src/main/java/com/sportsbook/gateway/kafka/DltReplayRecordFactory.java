package com.sportsbook.gateway.kafka;

import static org.springframework.kafka.support.KafkaHeaders.DELIVERY_ATTEMPT;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.support.serializer.SerializationUtils;
import org.springframework.stereotype.Component;

/** Creates raw source records for controlled replay of gateway dead letters. */
@Component
public final class DltReplayRecordFactory {

  private static final Set<String> FRAMEWORK_HEADERS =
      Set.of(
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
          DELIVERY_ATTEMPT,
          SerializationUtils.KEY_DESERIALIZER_EXCEPTION_HEADER,
          SerializationUtils.VALUE_DESERIALIZER_EXCEPTION_HEADER);

  private final Map<String, String> deadLetterToSource;

  public DltReplayRecordFactory(GatewayTopicProperties topics) {
    deadLetterToSource =
        topics.sourceToDeadLetter().entrySet().stream()
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getValue, Map.Entry::getKey));
  }

  public ProducerRecord<byte[], byte[]> replay(ConsumerRecord<byte[], byte[]> deadLetterRecord) {
    String sourceTopic = deadLetterToSource.get(deadLetterRecord.topic());
    if (sourceTopic == null) {
      throw new IllegalArgumentException(
          "Record is not from an exact gateway DLT: " + deadLetterRecord.topic());
    }
    RecordHeaders sanitized = new RecordHeaders();
    for (Header header : deadLetterRecord.headers()) {
      if (!FRAMEWORK_HEADERS.contains(header.key())) {
        sanitized.add(header.key(), clone(header.value()));
      }
    }
    return new ProducerRecord<>(
        sourceTopic,
        deadLetterRecord.partition(),
        clone(deadLetterRecord.key()),
        clone(deadLetterRecord.value()),
        sanitized);
  }

  private static byte[] clone(byte[] bytes) {
    return bytes == null ? null : bytes.clone();
  }
}
