package com.sportsbook.gateway.kafka;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;

/** Publishes failed source records to a bounded, same-partition dead-letter destination. */
@Configuration(proxyBeanMethods = false)
public class GatewayDeadLetterConfiguration {

  @Bean
  ProducerFactory<byte[], byte[]> gatewayProducerFactory(
      KafkaProperties properties, SslBundles sslBundles) {
    Map<String, Object> configuration =
        new HashMap<>(properties.buildProducerProperties(sslBundles));
    configuration.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
    configuration.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
    configuration.put(ProducerConfig.ACKS_CONFIG, "all");
    configuration.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
    return new DefaultKafkaProducerFactory<>(configuration);
  }

  @Bean
  KafkaTemplate<byte[], byte[]> gatewayKafkaTemplate(
      ProducerFactory<byte[], byte[]> gatewayProducerFactory) {
    return new KafkaTemplate<>(gatewayProducerFactory);
  }

  @Bean
  DeadLetterPublishingRecoverer gatewayDeadLetterRecoverer(
      KafkaTemplate<byte[], byte[]> gatewayKafkaTemplate,
      GatewayTopicProperties topics,
      GatewayKafkaProperties properties) {
    DeadLetterPublishingRecoverer recoverer =
        new DeadLetterPublishingRecoverer(
            gatewayKafkaTemplate,
            (record, exception) ->
                topics.deadLetterDestination(record.topic(), record.partition()));
    recoverer.setFailIfSendResultIsError(true);
    recoverer.setWaitForSendResultTimeout(properties.dltWaitTimeout());
    recoverer.setTimeoutBuffer(properties.dltTimeoutBuffer().toMillis());
    recoverer.setVerifyPartition(false);
    recoverer.setStripPreviousExceptionHeaders(true);
    return recoverer;
  }
}
