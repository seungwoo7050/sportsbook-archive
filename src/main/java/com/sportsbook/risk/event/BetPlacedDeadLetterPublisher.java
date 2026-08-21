package com.sportsbook.risk.event;

import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/** Publishes permanent input failures and waits for the DLT broker acknowledgment. */
@Component
public class BetPlacedDeadLetterPublisher {
  private static final Duration PUBLISH_TIMEOUT = Duration.ofSeconds(10);

  private final KafkaTemplate<String, byte[]> kafka;
  private final EventTopics topics;
  private final MeterRegistry meters;

  public BetPlacedDeadLetterPublisher(
      KafkaTemplate<String, byte[]> kafka, EventTopics topics, MeterRegistry meters) {
    this.kafka = Objects.requireNonNull(kafka, "kafka");
    this.topics = Objects.requireNonNull(topics, "topics");
    this.meters = Objects.requireNonNull(meters, "meters");
  }

  public void publishAndAwait(String key, byte[] payload, BetPlacedFailureReason reason) {
    Objects.requireNonNull(reason, "reason");
    ProducerRecord<String, byte[]> record =
        new ProducerRecord<>(topics.betPlacedDlt(), key, payload);
    record
        .headers()
        .add(
            new RecordHeader("risk-dlt-reason", reason.name().getBytes(StandardCharsets.US_ASCII)));
    try {
      kafka.send(record).get(PUBLISH_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
      meters.counter("risk_bet_placed_dlt_total", "reason", reason.name()).increment();
    } catch (InterruptedException failure) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("interrupted while publishing bet.placed DLT", failure);
    } catch (Exception failure) {
      throw new IllegalStateException("failed to publish bet.placed DLT", failure);
    }
  }
}
