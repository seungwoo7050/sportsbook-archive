package com.sportsbook.settlement.config;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;

/** Dedicated bounded raw-byte producer for exact dead-letter publication. */
@Configuration
public class RawKafkaProducerConfiguration {

  public static final String OPERATIONS = "settlementRawKafkaOperations";

  @Bean(OPERATIONS)
  KafkaOperations<byte[], byte[]> settlementRawKafkaOperations(KafkaProperties properties) {
    return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(producerProperties(properties)));
  }

  static Map<String, Object> producerProperties(KafkaProperties properties) {
    Map<String, Object> configured = new HashMap<>(properties.buildProducerProperties());
    configured.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
    configured.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
    configured.put(ProducerConfig.ACKS_CONFIG, "all");
    configured.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
    configured.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
    configured.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 5_000);
    configured.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 5_000);
    configured.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 10_000);
    configured.put(ProducerConfig.CLIENT_ID_CONFIG, "settlement-service-dlt");
    return Map.copyOf(configured);
  }
}
