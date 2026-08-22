package com.sportsbook.betting.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.jupiter.api.Test;

class KafkaConfigTest {

  @Test
  void requiresBrokerAcknowledgementAndIdempotence() {
    Map<String, Object> properties = KafkaConfig.producerProperties("broker:9092");

    assertThat(properties.get(ProducerConfig.ACKS_CONFIG)).isEqualTo("all");
    assertThat(properties.get(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG)).isEqualTo(true);
    assertThat(properties.get(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION)).isEqualTo(5);
    assertThat(properties.get(ProducerConfig.MAX_BLOCK_MS_CONFIG)).isEqualTo(5_000);
    assertThat(properties.get(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG)).isEqualTo(5_000);
    assertThat(properties.get(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG)).isEqualTo(10_000);
  }
}
