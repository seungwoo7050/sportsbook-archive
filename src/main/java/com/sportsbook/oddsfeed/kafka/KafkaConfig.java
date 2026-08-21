package com.sportsbook.oddsfeed.kafka;

import com.sportsbook.oddsfeed.config.PublishProperties;
import java.util.HashMap;
import java.util.Map;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

@Configuration
public class KafkaConfig {

  @Bean
  public ProducerFactory<String, SpecificRecord> avroProducerFactory(
      KafkaProperties properties, PublishProperties publishProperties) {
    Map<String, Object> settings = new HashMap<>(properties.buildProducerProperties());
    settings.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    settings.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, AvroSerializer.class);
    settings.put(
        ProducerConfig.MAX_BLOCK_MS_CONFIG, publishProperties.brokerAckTimeout().toMillis());
    return new DefaultKafkaProducerFactory<>(settings);
  }

  @Bean
  public KafkaTemplate<String, SpecificRecord> avroKafkaTemplate(
      ProducerFactory<String, SpecificRecord> producerFactory) {
    return new KafkaTemplate<>(producerFactory);
  }
}
