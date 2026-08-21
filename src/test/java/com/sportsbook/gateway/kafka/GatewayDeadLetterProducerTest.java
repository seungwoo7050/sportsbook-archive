package com.sportsbook.gateway.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.ProducerFactory;

@SpringBootTest(
    properties = {"spring.main.web-application-type=none", "management.tracing.enabled=false"})
class GatewayDeadLetterProducerTest {

  @Autowired private ProducerFactory<byte[], byte[]> producers;
  @Autowired private GatewayKafkaProperties properties;

  @Test
  void configuresRawIdempotentPublicationWithBoundedStages() {
    assertThat(producers.getConfigurationProperties())
        .containsEntry(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class)
        .containsEntry(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class)
        .containsEntry(ProducerConfig.ACKS_CONFIG, "all")
        .containsEntry(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
    assertThat(producers.getConfigurationProperties().get(ProducerConfig.MAX_BLOCK_MS_CONFIG))
        .hasToString("5000");
    assertThat(producers.getConfigurationProperties().get(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG))
        .hasToString("5000");
    assertThat(
            producers.getConfigurationProperties().get(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG))
        .hasToString("10000");
    assertThat(properties.dltWaitTimeout()).isEqualTo(Duration.ofSeconds(11));
    assertThat(properties.dltTimeoutBuffer()).isEqualTo(Duration.ofSeconds(1));
  }
}
