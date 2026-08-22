package com.sportsbook.betting.config;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

@Configuration
public class KafkaConfig {

  private final String bootstrapServers;

  public KafkaConfig(@Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
    this.bootstrapServers = bootstrapServers;
  }

  @Bean
  ProducerFactory<String, byte[]> bettingProducerFactory() {
    return new DefaultKafkaProducerFactory<>(producerProperties(bootstrapServers));
  }

  @Bean
  KafkaTemplate<String, byte[]> bettingKafkaTemplate(
      ProducerFactory<String, byte[]> bettingProducerFactory) {
    return new KafkaTemplate<>(bettingProducerFactory);
  }

  static Map<String, Object> producerProperties(String bootstrapServers) {
    Map<String, Object> properties = new HashMap<>();
    properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
    properties.put(ProducerConfig.ACKS_CONFIG, "all");
    properties.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
    properties.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
    properties.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 5_000);
    properties.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 5_000);
    properties.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 10_000);
    properties.put(ProducerConfig.CLIENT_ID_CONFIG, "betting-service-outbox");
    return Map.copyOf(properties);
  }
}
