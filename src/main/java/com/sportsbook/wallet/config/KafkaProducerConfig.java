package com.sportsbook.wallet.config;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

@Configuration
public class KafkaProducerConfig {

  private static final int MAX_IN_FLIGHT = 5;
  public static final Duration DELIVERY_TIMEOUT = Duration.ofSeconds(5L);
  public static final Duration MAX_BLOCK_TIME = Duration.ofSeconds(5L);
  private static final int REQUEST_TIMEOUT_MILLIS = 4_000;

  @Bean
  public ProducerFactory<String, byte[]> walletProducerFactory(KafkaProperties properties) {
    Map<String, Object> configuration = new HashMap<>(properties.buildProducerProperties());
    configuration.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    configuration.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
    configuration.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
    configuration.put(ProducerConfig.ACKS_CONFIG, "all");
    configuration.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, MAX_IN_FLIGHT);
    configuration.put(
        ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, Math.toIntExact(DELIVERY_TIMEOUT.toMillis()));
    configuration.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, REQUEST_TIMEOUT_MILLIS);
    configuration.put(
        ProducerConfig.MAX_BLOCK_MS_CONFIG, Math.toIntExact(MAX_BLOCK_TIME.toMillis()));
    return new DefaultKafkaProducerFactory<>(configuration);
  }

  @Bean
  public KafkaTemplate<String, byte[]> walletKafkaTemplate(
      ProducerFactory<String, byte[]> walletProducerFactory) {
    return new KafkaTemplate<>(walletProducerFactory);
  }
}
