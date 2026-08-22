package com.sportsbook.settlement.outbox;

import com.sportsbook.settlement.config.RawKafkaProducerConfiguration;
import com.sportsbook.settlement.config.SettlementRuntimeProperties;
import com.sportsbook.settlement.config.SettlementWorkerConfiguration;
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

  private final OutboxEventRepository repository;
  private final KafkaOperations<byte[], byte[]> kafka;
  private final SettlementRuntimeProperties runtime;
  private final Clock clock;

  public OutboxPublisher(
      OutboxEventRepository repository,
      @Qualifier(RawKafkaProducerConfiguration.OPERATIONS) KafkaOperations<byte[], byte[]> kafka,
      SettlementRuntimeProperties runtime,
      Clock clock) {
    this.repository = repository;
    this.kafka = kafka;
    this.runtime = runtime;
    this.clock = clock;
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
      throw new KafkaException("Interrupted while publishing outbox event", exception);
    } catch (ExecutionException | TimeoutException exception) {
      throw new KafkaException("Failed to publish outbox event", exception);
    }
  }
}
