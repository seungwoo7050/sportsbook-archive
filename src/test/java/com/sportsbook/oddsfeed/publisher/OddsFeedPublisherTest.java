package com.sportsbook.oddsfeed.publisher;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.oddsfeed.config.KafkaTopicsProperties;
import com.sportsbook.oddsfeed.config.PublishProperties;
import com.sportsbook.oddsfeed.kafka.BrokerAvailability;
import com.sportsbook.protocol.event.OddsChanged;
import com.sportsbook.protocol.value.EventId;
import com.sportsbook.protocol.value.MarketId;
import com.sportsbook.protocol.value.Odds;
import com.sportsbook.protocol.value.SelectionId;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.avro.specific.SpecificRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

class OddsFeedPublisherTest {

  @Test
  void publishesThresholdedChangesWithEventKeys() {
    RecordingKafkaTemplate kafka = new RecordingKafkaTemplate();
    OddsFeedPublisher publisher = publisher(kafka, new BrokerAvailability());
    EventId eventId = new EventId(UUID.randomUUID());
    MarketId marketId = new MarketId(UUID.randomUUID());
    SelectionId selectionId = new SelectionId(UUID.randomUUID());
    Instant changedAt = Instant.parse("2026-06-01T18:00:00Z");

    assertThat(
            publisher.publishOddsChanged(
                eventId,
                marketId,
                selectionId,
                Odds.ofDecimal("2.00"),
                Odds.ofDecimal("2.01"),
                changedAt,
                false))
        .isFalse();
    assertThat(kafka.payload).isNull();
    assertThat(publisher.isSignificantChange(Odds.ofDecimal("2.00"), Odds.ofDecimal("2.03")))
        .isTrue();

    assertThat(
            publisher.publishOddsChanged(
                eventId,
                marketId,
                selectionId,
                Odds.ofDecimal("2.00"),
                Odds.ofDecimal("2.01"),
                changedAt,
                true))
        .isTrue();
    assertThat(kafka.topic).isEqualTo("odds.changed");
    assertThat(kafka.key).isEqualTo(eventId.value().toString());
    assertThat(kafka.payload).isInstanceOf(OddsChanged.class);
    assertThat(((OddsChanged) kafka.payload).getNewOdds()).isEqualTo("2.0100");
  }

  private static OddsFeedPublisher publisher(
      RecordingKafkaTemplate kafka, BrokerAvailability availability) {
    return new OddsFeedPublisher(
        kafka,
        new KafkaTopicsProperties("odds.changed", "market", "lifecycle", "result"),
        new PublishProperties(new BigDecimal("0.01"), Duration.ofSeconds(1)),
        availability);
  }

  private static final class RecordingKafkaTemplate extends KafkaTemplate<String, SpecificRecord> {
    private String topic;
    private String key;
    private SpecificRecord payload;

    private RecordingKafkaTemplate() {
      super(new DefaultKafkaProducerFactory<>(Map.of()));
    }

    @Override
    public CompletableFuture<SendResult<String, SpecificRecord>> send(
        String topic, String key, SpecificRecord payload) {
      this.topic = topic;
      this.key = key;
      this.payload = payload;
      return CompletableFuture.completedFuture(null);
    }
  }
}
