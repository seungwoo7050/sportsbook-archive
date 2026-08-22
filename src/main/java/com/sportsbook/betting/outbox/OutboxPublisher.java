package com.sportsbook.betting.outbox;

import java.time.Clock;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OutboxPublisher {

  private static final Logger LOG = LoggerFactory.getLogger(OutboxPublisher.class);
  private static final int BATCH_SIZE = 100;
  static final long TIMEOUT_SECONDS = 11;

  private final OutboxEventRepository repository;
  private final KafkaTemplate<String, byte[]> kafka;
  private final Clock clock;

  public OutboxPublisher(
      OutboxEventRepository repository, KafkaTemplate<String, byte[]> kafka, Clock clock) {
    this.repository = repository;
    this.kafka = kafka;
    this.clock = clock;
  }

  @Scheduled(
      fixedDelayString = "${betting.outbox.poll-interval-ms:1000}",
      scheduler = "outboxTaskScheduler")
  @Transactional
  public void publishPending() {
    for (OutboxEvent event : repository.findUnpublished(PageRequest.of(0, BATCH_SIZE))) {
      publish(event);
    }
  }

  private void publish(OutboxEvent event) {
    try {
      ProducerRecord<String, byte[]> record =
          new ProducerRecord<>(event.topic(), event.partitionKey(), event.payload());
      kafka.send(record).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
      event.markPublished(clock.instant());
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      LOG.warn("Interrupted publishing outbox event {}", event.eventId());
    } catch (Exception exception) {
      LOG.warn("Outbox event {} remains pending", event.eventId());
    }
  }
}
