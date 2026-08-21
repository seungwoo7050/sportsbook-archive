package com.sportsbook.gateway.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;

@SpringBootTest(
    properties = {
      "spring.main.web-application-type=none",
      "gateway.topics.odds-changed=gateway.recovery.test",
      "logging.level.kafka=ERROR"
    })
@EmbeddedKafka(
    partitions = 2,
    topics = "gateway.recovery.test",
    brokerProperties = "auto.create.topics.enable=false",
    bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@Import(GatewayRecoveryFailureIntegrationTest.RecoveryProbe.class)
class GatewayRecoveryFailureIntegrationTest {

  @Autowired private KafkaTemplate<byte[], byte[]> kafka;
  @Autowired private KafkaListenerEndpointRegistry listeners;
  @Autowired private EmbeddedKafkaBroker broker;
  @Autowired private RecoveryProbe probe;

  @Test
  void keepsTheFailedOffsetWhenTheDltLacksTheSourcePartition() throws Exception {
    broker.addTopics(new NewTopic("gateway.recovery.test.DLT", 1, (short) 1));
    probe.reset();
    ContainerTestUtils.waitForAssignment(listeners.getListenerContainer("recovery-probe"), 2);
    send("first");
    assertThat(probe.redelivered.await(35, TimeUnit.SECONDS)).isTrue();
    assertThat(probe.keys).hasSizeGreaterThanOrEqualTo(4).allMatch("first"::equals);
    listeners.getListenerContainer("recovery-probe").stop();
    assertFailedOffsetRemainsNext();
  }

  private void send(String key) throws Exception {
    kafka
        .send("gateway.recovery.test", 1, key.getBytes(StandardCharsets.UTF_8), new byte[] {1})
        .get(5, TimeUnit.SECONDS);
  }

  private void assertFailedOffsetRemainsNext() throws Exception {
    try (Admin admin =
        Admin.create(
            Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString()))) {
      var committed =
          admin
              .listConsumerGroupOffsets("gateway-recovery-test")
              .partitionsToOffsetAndMetadata()
              .get(5, TimeUnit.SECONDS);
      assertThat(committed.get(new TopicPartition("gateway.recovery.test", 1)).offset()).isZero();
    }
  }

  static final class RecoveryProbe {
    private final List<String> keys = new CopyOnWriteArrayList<>();
    private CountDownLatch redelivered = new CountDownLatch(1);

    @KafkaListener(
        id = "recovery-probe",
        topics = "gateway.recovery.test",
        groupId = "gateway-recovery-test",
        autoStartup = "true")
    void receive(ConsumerRecord<byte[], byte[]> record) {
      keys.add(new String(record.key(), StandardCharsets.UTF_8));
      if (keys.size() == 4) {
        redelivered.countDown();
      }
      throw new IllegalStateException("delivery remains unavailable");
    }

    void reset() {
      keys.clear();
      redelivered = new CountDownLatch(1);
    }
  }
}
