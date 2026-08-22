package com.sportsbook.settlement.event;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaOperations;

/** Republishes a poison record to the exact corresponding DLT partition with raw bytes intact. */
public final class ExactDeadLetterRecoverer {

  static final String ORIGINAL_TOPIC = "settlement-dlt-original-topic";
  static final String ORIGINAL_PARTITION = "settlement-dlt-original-partition";
  static final String ORIGINAL_OFFSET = "settlement-dlt-original-offset";
  static final String ORIGINAL_TIMESTAMP = "settlement-dlt-original-timestamp";
  static final String CONSUMER_GROUP = "settlement-dlt-consumer-group";
  static final String EXCEPTION_TYPE = "settlement-dlt-exception-type";

  private final KafkaOperations<byte[], byte[]> kafka;
  private final String consumerGroup;
  private final Duration sendTimeout;

  public ExactDeadLetterRecoverer(
      KafkaOperations<byte[], byte[]> kafka, String consumerGroup, Duration sendTimeout) {
    this.kafka = Objects.requireNonNull(kafka, "kafka");
    this.consumerGroup = Objects.requireNonNull(consumerGroup, "consumerGroup");
    this.sendTimeout = Objects.requireNonNull(sendTimeout, "sendTimeout");
  }

  public void recover(ConsumerRecord<byte[], byte[]> source, Exception failure) {
    RecordHeaders headers = new RecordHeaders(source.headers());
    headers.add(ORIGINAL_TOPIC, utf8(source.topic()));
    headers.add(ORIGINAL_PARTITION, integer(source.partition()));
    headers.add(ORIGINAL_OFFSET, longBytes(source.offset()));
    headers.add(ORIGINAL_TIMESTAMP, longBytes(source.timestamp()));
    headers.add(CONSUMER_GROUP, utf8(consumerGroup));
    headers.add(EXCEPTION_TYPE, utf8(failure.getClass().getName()));
    ProducerRecord<byte[], byte[]> deadLetter =
        new ProducerRecord<>(
            source.topic() + ".DLT",
            source.partition(),
            null,
            source.key(),
            source.value(),
            headers);
    try {
      kafka.send(deadLetter).get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new KafkaException("Interrupted while publishing exact DLT record", exception);
    } catch (ExecutionException | TimeoutException exception) {
      throw new KafkaException("Failed to publish exact DLT record", exception);
    }
  }

  private static byte[] utf8(String value) {
    return value.getBytes(StandardCharsets.UTF_8);
  }

  private static byte[] integer(int value) {
    return ByteBuffer.allocate(Integer.BYTES).putInt(value).array();
  }

  private static byte[] longBytes(long value) {
    return ByteBuffer.allocate(Long.BYTES).putLong(value).array();
  }
}
