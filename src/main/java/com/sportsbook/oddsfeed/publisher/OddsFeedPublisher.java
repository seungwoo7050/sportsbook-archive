package com.sportsbook.oddsfeed.publisher;

import com.sportsbook.oddsfeed.config.KafkaTopicsProperties;
import com.sportsbook.oddsfeed.config.PublishProperties;
import com.sportsbook.oddsfeed.kafka.BrokerAvailability;
import com.sportsbook.protocol.event.OddsChanged;
import com.sportsbook.protocol.value.EventId;
import com.sportsbook.protocol.value.MarketId;
import com.sportsbook.protocol.value.Odds;
import com.sportsbook.protocol.value.SelectionId;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.avro.specific.SpecificRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class OddsFeedPublisher {

  private static final int RELATIVE_SCALE = 6;

  private final KafkaTemplate<String, SpecificRecord> kafka;
  private final KafkaTopicsProperties topics;
  private final PublishProperties properties;
  private final BrokerAvailability availability;

  public OddsFeedPublisher(
      KafkaTemplate<String, SpecificRecord> kafka,
      KafkaTopicsProperties topics,
      PublishProperties properties,
      BrokerAvailability availability) {
    this.kafka = kafka;
    this.topics = topics;
    this.properties = properties;
    this.availability = availability;
  }

  public boolean publishOddsChanged(
      EventId eventId,
      MarketId marketId,
      SelectionId selectionId,
      Odds previous,
      Odds next,
      Instant changedAt,
      boolean forceCurrentSnapshot) {
    if (!forceCurrentSnapshot && !isSignificantChange(previous, next)) {
      return false;
    }
    send(
        topics.oddsChanged(),
        eventId,
        new OddsChanged(
            eventId.value().toString(),
            marketId.value().toString(),
            selectionId.value().toString(),
            previous.decimal().toPlainString(),
            next.decimal().toPlainString(),
            changedAt));
    return true;
  }

  boolean isSignificantChange(Odds previous, Odds next) {
    BigDecimal difference = next.decimal().subtract(previous.decimal()).abs();
    BigDecimal relative =
        difference.divide(previous.decimal(), RELATIVE_SCALE, RoundingMode.HALF_EVEN);
    return relative.compareTo(properties.oddsChangeThreshold()) >= 0;
  }

  public boolean isHealthy() {
    return availability.isAvailable();
  }

  private void send(String topic, EventId eventId, SpecificRecord event) {
    try {
      kafka
          .send(topic, eventId.value().toString(), event)
          .get(properties.brokerAckTimeout().toMillis(), TimeUnit.MILLISECONDS);
      availability.markAvailable();
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      availability.markUnavailable();
      throw new KafkaPublishException("Interrupted while awaiting Kafka acknowledgement", error);
    } catch (ExecutionException | TimeoutException | RuntimeException error) {
      availability.markUnavailable();
      throw new KafkaPublishException("Kafka did not acknowledge " + topic, error);
    }
  }
}
