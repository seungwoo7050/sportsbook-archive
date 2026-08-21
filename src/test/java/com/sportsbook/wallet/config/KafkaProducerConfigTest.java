package com.sportsbook.wallet.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;

class KafkaProducerConfigTest {

  @Test
  void boundsIdempotentProducerDeliveryBeforeTheLeaseExpires() {
    KafkaProperties properties = new KafkaProperties();
    properties.setBootstrapServers(java.util.List.of("localhost:9092"));
    var factory =
        (DefaultKafkaProducerFactory<String, byte[]>)
            new KafkaProducerConfig().walletProducerFactory(properties);
    Map<String, Object> configuration = factory.getConfigurationProperties();

    assertThat(configuration)
        .containsEntry(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true)
        .containsEntry(ProducerConfig.ACKS_CONFIG, "all")
        .containsEntry(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5)
        .containsEntry(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 5_000)
        .containsEntry(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 4_000)
        .containsEntry(ProducerConfig.MAX_BLOCK_MS_CONFIG, 5_000);
    assertThat(KafkaProducerConfig.DELIVERY_TIMEOUT).isEqualTo(java.time.Duration.ofSeconds(5L));
    assertThat(KafkaProducerConfig.MAX_BLOCK_TIME).isEqualTo(java.time.Duration.ofSeconds(5L));
  }
}
