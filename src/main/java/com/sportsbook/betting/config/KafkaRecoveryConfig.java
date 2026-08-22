package com.sportsbook.betting.config;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

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

  @Bean
  CommonErrorHandler kafkaConsumerErrorHandler(
      @Qualifier("rawDltKafkaTemplate") KafkaTemplate<byte[], byte[]> kafka) {
    return errorHandler(kafka, 1_000L);
  }

  static DefaultErrorHandler errorHandler(
      KafkaTemplate<byte[], byte[]> kafka, long retryDelayMillis) {
    DefaultErrorHandler handler =
        new DefaultErrorHandler(
            recoverer(kafka), new FixedBackOff(retryDelayMillis, FixedBackOff.UNLIMITED_ATTEMPTS));
    handler.setClassifications(Map.of(PermanentKafkaException.class, false), true);
    return handler;
  }

  static DeadLetterPublishingRecoverer recoverer(KafkaTemplate<byte[], byte[]> kafka) {
    DeadLetterPublishingRecoverer recoverer =
        new DeadLetterPublishingRecoverer(
            kafka,
            (record, failure) -> new TopicPartition(record.topic() + ".DLT", record.partition()));
    recoverer.setVerifyPartition(false);
    recoverer.setFailIfSendResultIsError(true);
    recoverer.setWaitForSendResultTimeout(Duration.ofSeconds(11));
    return recoverer;
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
