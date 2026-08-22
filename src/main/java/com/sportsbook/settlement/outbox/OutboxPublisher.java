package com.sportsbook.settlement.outbox;

import com.sportsbook.settlement.config.RawKafkaProducerConfiguration;
import com.sportsbook.settlement.config.SettlementRuntimeProperties;
import com.sportsbook.settlement.config.SettlementWorkerConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Publishes locked outbox rows at least once and marks them only after broker acknowledgement. */
@Component
public class OutboxPublisher {

  private static final long SEND_TIMEOUT_SECONDS = 11;
  public static final String PUBLISHED_METRIC = "settlement.outbox.published";
  public static final String FAILURE_METRIC = "settlement.outbox.publish.failures";

  private final OutboxEventRepository repository;
  private final KafkaOperations<byte[], byte[]> kafka;
  private final SettlementRuntimeProperties runtime;
  private final Clock clock;
  private final MeterRegistry meters;

  public OutboxPublisher(
      OutboxEventRepository repository,
      @Qualifier(RawKafkaProducerConfiguration.OPERATIONS) KafkaOperations<byte[], byte[]> kafka,
      SettlementRuntimeProperties runtime,
      Clock clock,
      MeterRegistry meters) {
    this.repository = repository;
    this.kafka = kafka;
    this.runtime = runtime;
    this.clock = clock;
    this.meters = meters;
  }

  @Transactional
  @Scheduled(
      fixedDelayString = "${settlement.outbox.interval:PT1S}",
      scheduler = SettlementWorkerConfiguration.OUTBOX)
  public int publishBatch() {
    var pending = repository.lockNextUnpublished(runtime.batchSize());
    for (OutboxEvent event : pending) {
      publish(event);
      event.markPublished(clock.instant());
      meters.counter(PUBLISHED_METRIC, "topic", event.topic()).increment();
    }
    return pending.size();
  }

  private void publish(OutboxEvent event) {
    ProducerRecord<byte[], byte[]> record =
        new ProducerRecord<>(
            event.topic(), event.partitionKey().getBytes(StandardCharsets.UTF_8), event.payload());
    try {
      kafka.send(record).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      meters.counter(FAILURE_METRIC, "topic", event.topic()).increment();
      throw new KafkaException("Interrupted while publishing outbox event", exception);
    } catch (ExecutionException | TimeoutException exception) {
      meters.counter(FAILURE_METRIC, "topic", event.topic()).increment();
      throw new KafkaException("Failed to publish outbox event", exception);
    }
  }
}
