package com.sportsbook.admin.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;

class AuditKafkaConfigurationTest {

  @Test
  void pinsSafeProducerDeliverySettings() {
    var configuration = new AuditKafkaConfiguration("broker-1:9092");
    var factory =
        (DefaultKafkaProducerFactory<String, byte[]>) configuration.auditProducerFactory();

    Map<String, Object> properties = factory.getConfigurationProperties();

    assertThat(properties)
        .containsEntry(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "broker-1:9092")
        .containsEntry(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class)
        .containsEntry(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class)
        .containsEntry(ProducerConfig.ACKS_CONFIG, "all")
        .containsEntry(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true)
        .containsEntry(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5)
        .containsEntry(ProducerConfig.CLIENT_ID_CONFIG, "admin-api-audit");
  }
}
