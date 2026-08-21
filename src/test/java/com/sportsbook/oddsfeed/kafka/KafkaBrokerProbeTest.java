package com.sportsbook.oddsfeed.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.sportsbook.oddsfeed.config.KafkaTopicsProperties;
import java.util.List;
import java.util.Map;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.PartitionInfo;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

class KafkaBrokerProbeTest {

  @Test
  void updatesAvailabilityWithoutWaitingForARecordSend() {
    StubKafkaTemplate kafka = new StubKafkaTemplate();
    BrokerAvailability availability = new BrokerAvailability();
    KafkaBrokerProbe probe =
        new KafkaBrokerProbe(
            kafka,
            new KafkaTopicsProperties("odds", "market", "lifecycle", "result"),
            availability);

    probe.probe();
    assertThat(availability.isAvailable()).isTrue();

    kafka.fail = true;
    probe.probe();
    assertThat(availability.isAvailable()).isFalse();
  }

  @Test
  void initializesTheDefaultProbeSchedule() {
    assertThatCode(
            () -> {
              try (var context = new AnnotationConfigApplicationContext(ProbeConfiguration.class)) {
                assertThat(context.getBean(KafkaBrokerProbe.class)).isNotNull();
              }
            })
        .doesNotThrowAnyException();
  }

  @Configuration(proxyBeanMethods = false)
  @EnableScheduling
  static class ProbeConfiguration {

    @Bean
    StubKafkaTemplate kafkaTemplate() {
      return new StubKafkaTemplate();
    }

    @Bean
    KafkaTopicsProperties kafkaTopicsProperties() {
      return new KafkaTopicsProperties("odds", "market", "lifecycle", "result");
    }

    @Bean
    BrokerAvailability brokerAvailability() {
      return new BrokerAvailability();
    }

    @Bean
    KafkaBrokerProbe kafkaBrokerProbe(
        StubKafkaTemplate kafka, KafkaTopicsProperties topics, BrokerAvailability availability) {
      return new KafkaBrokerProbe(kafka, topics, availability);
    }
  }

  private static final class StubKafkaTemplate extends KafkaTemplate<String, SpecificRecord> {
    private boolean fail;

    private StubKafkaTemplate() {
      super(new DefaultKafkaProducerFactory<>(Map.of()));
    }

    @Override
    public List<PartitionInfo> partitionsFor(String topic) {
      if (fail) {
        throw new KafkaException("broker unavailable");
      }
      return List.of(new PartitionInfo(topic, 0, Node.noNode(), null, null));
    }
  }
}
