package com.sportsbook.wallet.persistence;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.sportsbook.wallet.config.KafkaProducerConfig;
import com.sportsbook.wallet.outbox.KafkaOutboxDispatcher;
import com.sportsbook.wallet.outbox.OutboxAppender;
import com.sportsbook.wallet.outbox.OutboxPublisher;
import com.sportsbook.wallet.outbox.OutboxRetryPolicy;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.StreamSupport;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest(properties = "spring.test.database.replace=NONE")
@Testcontainers
@EmbeddedKafka(partitions = 1, topics = KafkaOutboxDeliveryTest.TOPIC)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({OutboxAppender.class, OutboxDeliveryRepository.class, OutboxStreamLock.class})
class KafkaOutboxDeliveryTest extends OutboxDeliveryRepositoryFixture {
  static final String TOPIC = "wallet.debited.v1";

  @Autowired EmbeddedKafkaBroker broker;
  @Autowired OutboxDeliveryRepository delivery;

  @Test
  void publishesOnlyAfterBrokerAckWithOneCanonicalEventHeader() {
    persist("operation-kafka", "user-1", "dedup-kafka", Instant.EPOCH);
    var event = events.findAll().get(0);
    KafkaProperties properties = new KafkaProperties();
    properties.setBootstrapServers(List.of(broker.getBrokersAsString()));
    KafkaProducerConfig configuration = new KafkaProducerConfig();
    var producerFactory = configuration.walletProducerFactory(properties);
    var template = configuration.walletKafkaTemplate(producerFactory);
    var consumerFactory =
        new DefaultKafkaConsumerFactory<>(
            KafkaTestUtils.consumerProps("wallet-outbox-test", "true", broker),
            new StringDeserializer(),
            new ByteArrayDeserializer());

    try (var consumer = consumerFactory.createConsumer()) {
      broker.consumeFromAnEmbeddedTopic(consumer, TOPIC);
      OutboxPublisher publisher =
          new OutboxPublisher(
              delivery,
              new KafkaOutboxDispatcher(template),
              new OutboxRetryPolicy(Duration.ofMillis(10), Duration.ofSeconds(1)),
              Runnable::run,
              "embedded-worker",
              1,
              1,
              Duration.ofSeconds(30));

      assertThat(isUnpublished(event.eventId())).isTrue();
      publisher.poll();
      var record = KafkaTestUtils.getSingleRecord(consumer, TOPIC, Duration.ofSeconds(10));
      var headers =
          StreamSupport.stream(
                  record.headers().headers(KafkaOutboxDispatcher.EVENT_ID_HEADER).spliterator(),
                  false)
              .toList();

      assertThat(record.key()).isEqualTo(event.partitionKey());
      assertThat(record.value()).containsExactly(event.payload());
      assertThat(headers)
          .singleElement()
          .extracting(header -> new String(header.value(), US_ASCII))
          .isEqualTo(event.eventId().toString());
      await()
          .atMost(Duration.ofSeconds(10))
          .untilAsserted(() -> assertThat(isUnpublished(event.eventId())).isFalse());
    } finally {
      producerFactory.reset();
    }
  }

  private boolean isUnpublished(java.util.UUID eventId) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT published_at IS NULL FROM outbox_event WHERE event_id=?",
            Boolean.class,
            eventId));
  }
}
