package com.sportsbook.oddsfeed.load;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.oddsfeed.config.KafkaTopicsProperties;
import com.sportsbook.oddsfeed.config.PublishProperties;
import com.sportsbook.oddsfeed.kafka.AvroSerializer;
import com.sportsbook.oddsfeed.kafka.BrokerAvailability;
import com.sportsbook.oddsfeed.publisher.OddsFeedPublisher;
import com.sportsbook.protocol.value.EventId;
import com.sportsbook.protocol.value.MarketId;
import com.sportsbook.protocol.value.Odds;
import com.sportsbook.protocol.value.SelectionId;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.EmbeddedKafkaZKBroker;

@Tag("load")
class KafkaPublishThroughputTest {

  private static final int EVENT_COUNT = 1_000;
  private static final double MINIMUM_EVENTS_PER_SECOND = 50.0;
  private static final KafkaTopicsProperties TOPICS =
      new KafkaTopicsProperties(
          "odds.changed", "market.status.changed", "event.lifecycle", "match.result");

  private static EmbeddedKafkaBroker broker;
  private static DefaultKafkaProducerFactory<String, SpecificRecord> producerFactory;
  private static OddsFeedPublisher publisher;

  @BeforeAll
  static void startBroker() {
    broker = new EmbeddedKafkaZKBroker(1, true, 1, TOPICS.oddsChanged());
    broker.afterPropertiesSet();

    Map<String, Object> properties = new HashMap<>();
    properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString());
    properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, AvroSerializer.class);
    properties.put(ProducerConfig.ACKS_CONFIG, "1");
    properties.put(ProducerConfig.LINGER_MS_CONFIG, 5);
    properties.put(ProducerConfig.BATCH_SIZE_CONFIG, 32 * 1024);
    properties.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4");
    producerFactory = new DefaultKafkaProducerFactory<>(properties);
    publisher =
        new OddsFeedPublisher(
            new KafkaTemplate<>(producerFactory),
            TOPICS,
            new PublishProperties(new BigDecimal("0.01"), Duration.ofSeconds(5)),
            new BrokerAvailability());
  }

  @AfterAll
  static void stopBroker() {
    producerFactory.destroy();
    broker.destroy();
  }

  @Test
  void sustainsBrokerAcknowledgedThroughput() {
    EventId eventId = new EventId(UUID.fromString("00000000-0000-4000-8000-000000000001"));
    MarketId marketId = new MarketId(UUID.fromString("00000000-0000-4000-8000-000000000002"));
    SelectionId selectionId =
        new SelectionId(UUID.fromString("00000000-0000-4000-8000-000000000003"));
    Odds previous = Odds.ofDecimal("2.00");
    Odds next = Odds.ofDecimal("2.10");
    Instant changedAt = Instant.parse("2026-01-01T00:00:00Z");

    publisher.publishOddsChanged(eventId, marketId, selectionId, previous, next, changedAt, false);
    long startedAt = System.nanoTime();
    for (int index = 0; index < EVENT_COUNT; index++) {
      publisher.publishOddsChanged(
          eventId, marketId, selectionId, previous, next, changedAt, false);
    }
    long elapsedNanos = System.nanoTime() - startedAt;
    double rate = EVENT_COUNT / (elapsedNanos / (double) TimeUnit.SECONDS.toNanos(1));

    assertThat(rate).isGreaterThanOrEqualTo(MINIMUM_EVENTS_PER_SECOND);
  }
}
