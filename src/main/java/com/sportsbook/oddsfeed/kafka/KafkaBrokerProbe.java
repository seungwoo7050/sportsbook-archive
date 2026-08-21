package com.sportsbook.oddsfeed.kafka;

import com.sportsbook.oddsfeed.config.KafkaTopicsProperties;
import org.apache.avro.specific.SpecificRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public final class KafkaBrokerProbe {

  private final KafkaTemplate<String, SpecificRecord> kafka;
  private final KafkaTopicsProperties topics;
  private final BrokerAvailability availability;

  public KafkaBrokerProbe(
      KafkaTemplate<String, SpecificRecord> kafka,
      KafkaTopicsProperties topics,
      BrokerAvailability availability) {
    this.kafka = kafka;
    this.topics = topics;
    this.availability = availability;
  }

  @Scheduled(fixedDelayString = "${oddsfeed.publish.probe-interval:5000}")
  public void probe() {
    try {
      if (kafka.partitionsFor(topics.oddsChanged()).isEmpty()) {
        availability.markUnavailable();
      } else {
        availability.markAvailable();
      }
    } catch (RuntimeException error) {
      availability.markUnavailable();
    }
  }
}
