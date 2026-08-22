package com.sportsbook.betting.config;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

@Configuration
public class KafkaRecoveryConfig {

  private final String bootstrapServers;

  public KafkaRecoveryConfig(@Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
    this.bootstrapServers = bootstrapServers;
  }

  @Bean
  ProducerFactory<byte[], byte[]> rawDltProducerFactory() {
    return new DefaultKafkaProducerFactory<>(rawProducerProperties(bootstrapServers));
  }

  @Bean
  KafkaTemplate<byte[], byte[]> rawDltKafkaTemplate(
      ProducerFactory<byte[], byte[]> rawDltProducerFactory) {
    return new KafkaTemplate<>(rawDltProducerFactory);
  }

  static Map<String, Object> rawProducerProperties(String bootstrapServers) {
    Map<String, Object> properties =
        new HashMap<>(KafkaConfig.producerProperties(bootstrapServers));
    properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
    properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
    properties.put(ProducerConfig.CLIENT_ID_CONFIG, "betting-service-dlt");
    properties.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 5_000);
    properties.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 5_000);
    properties.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 10_000);
    return Map.copyOf(properties);
  }
}
