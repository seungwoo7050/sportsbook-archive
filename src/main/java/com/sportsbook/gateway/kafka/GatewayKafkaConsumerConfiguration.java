package com.sportsbook.gateway.kafka;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;

/** Configures event consumers to retain Kafka keys and values as their original bytes. */
@EnableKafka
@Configuration(proxyBeanMethods = false)
public class GatewayKafkaConsumerConfiguration {

  @Bean
  ConsumerFactory<byte[], byte[]> gatewayConsumerFactory(
      KafkaProperties properties, SslBundles sslBundles) {
    Map<String, Object> configuration =
        new HashMap<>(properties.buildConsumerProperties(sslBundles));
    configuration.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
    configuration.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
    configuration.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
    return new DefaultKafkaConsumerFactory<>(configuration);
  }

  @Bean
  ConcurrentKafkaListenerContainerFactory<byte[], byte[]> kafkaListenerContainerFactory(
      ConsumerFactory<byte[], byte[]> gatewayConsumerFactory,
      ObjectProvider<DefaultErrorHandler> errorHandler) {
    ConcurrentKafkaListenerContainerFactory<byte[], byte[]> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(gatewayConsumerFactory);
    factory.setBatchListener(false);
    errorHandler.ifAvailable(factory::setCommonErrorHandler);
    factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
    return factory;
  }
}
