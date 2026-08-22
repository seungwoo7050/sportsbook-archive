package com.sportsbook.betting.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.junit.jupiter.api.Test;

class KafkaRecoveryConfigTest {

  @Test
  void preservesRawKeysAndValuesForDltPublication() {
    Map<String, Object> properties = KafkaRecoveryConfig.rawProducerProperties("broker:9092");

    assertThat(properties.get(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG))
        .isEqualTo(ByteArraySerializer.class);
    assertThat(properties.get(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG))
        .isEqualTo(ByteArraySerializer.class);
    assertThat(properties.get(ProducerConfig.ACKS_CONFIG)).isEqualTo("all");
    assertThat(properties.get(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG)).isEqualTo(true);
    assertThat(properties.get(ProducerConfig.MAX_BLOCK_MS_CONFIG)).isEqualTo(5_000);
    assertThat(properties.get(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG)).isEqualTo(5_000);
    assertThat(properties.get(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG)).isEqualTo(10_000);
  }
}
