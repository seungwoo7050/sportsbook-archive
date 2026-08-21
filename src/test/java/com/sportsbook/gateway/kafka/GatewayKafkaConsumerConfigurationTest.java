package com.sportsbook.gateway.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;

@SpringBootTest(
    properties = {"spring.main.web-application-type=none", "management.tracing.enabled=false"})
class GatewayKafkaConsumerConfigurationTest {

  @Autowired private ConsumerFactory<byte[], byte[]> consumers;
  @Autowired private ConcurrentKafkaListenerContainerFactory<byte[], byte[]> containers;

  @Test
  void retainsRawKeysAndValuesWithoutAutoCommit() {
    assertThat(consumers.getConfigurationProperties())
        .containsEntry(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false)
        .containsEntry(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class)
        .containsEntry(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
  }

  @Test
  void acknowledgesOneRecordAtATime() {
    assertThat(containers.isBatchListener()).isFalse();
    assertThat(containers.getContainerProperties().getAckMode())
        .isEqualTo(ContainerProperties.AckMode.RECORD);
  }
}
