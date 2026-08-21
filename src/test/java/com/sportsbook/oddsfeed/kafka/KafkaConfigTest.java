package com.sportsbook.oddsfeed.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.oddsfeed.config.PublishProperties;
import java.math.BigDecimal;
import java.time.Duration;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;

class KafkaConfigTest {

  @Test
  void usesTypedSerializersAndBoundedMetadataWaits() {
    Duration timeout = Duration.ofMillis(1234);
    var factory =
        new KafkaConfig()
            .avroProducerFactory(
                new KafkaProperties(), new PublishProperties(new BigDecimal("0.01"), timeout));

    assertThat(factory).isInstanceOf(DefaultKafkaProducerFactory.class);
    assertThat(((DefaultKafkaProducerFactory<?, ?>) factory).getConfigurationProperties())
        .containsEntry(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class)
        .containsEntry(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, AvroSerializer.class)
        .containsEntry(ProducerConfig.MAX_BLOCK_MS_CONFIG, timeout.toMillis());
  }
}
