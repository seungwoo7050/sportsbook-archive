package com.sportsbook.settlement.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;

class RawKafkaProducerConfigurationTest {

  @Test
  void fixesRawSerializersDurabilityAndSendBounds() {
    KafkaProperties properties = new KafkaProperties();
    properties.setBootstrapServers(java.util.List.of("broker:9092"));

    Map<String, Object> configured = RawKafkaProducerConfiguration.producerProperties(properties);

    assertThat(configured)
        .containsEntry(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class)
        .containsEntry(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class)
        .containsEntry(ProducerConfig.ACKS_CONFIG, "all")
        .containsEntry(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true)
        .containsEntry(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5)
        .containsEntry(ProducerConfig.MAX_BLOCK_MS_CONFIG, 5_000)
        .containsEntry(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 5_000)
        .containsEntry(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 10_000);
  }
}
