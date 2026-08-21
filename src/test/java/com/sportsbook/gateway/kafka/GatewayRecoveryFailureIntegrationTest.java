package com.sportsbook.gateway.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewPartitions;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.utils.KafkaTestUtils;

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
@TestMethodOrder(OrderAnnotation.class)
class GatewayRecoveryFailureIntegrationTest {

  @Autowired private KafkaTemplate<byte[], byte[]> kafka;
  @Autowired private KafkaListenerEndpointRegistry listeners;
  @Autowired private EmbeddedKafkaBroker broker;
  @Autowired private RecoveryProbe probe;

  @Test
  @Order(1)
  void keepsTheFailedOffsetWhenTheDltLacksTheSourcePartition() throws Exception {
    broker.addTopics(new NewTopic("gateway.recovery.test.DLT", 1, (short) 1));
    startProbe();
    send("first");
    assertThat(probe.redelivered.await(35, TimeUnit.SECONDS)).isTrue();
    assertThat(probe.keys).hasSizeGreaterThanOrEqualTo(4).allMatch("first"::equals);
    listeners.getListenerContainer("recovery-probe").stop();
    assertThat(committedOffset()).isZero();
  }

  @Test
  @Order(2)
  void resumesThePartitionAfterDeadLetterRecoverySucceeds() throws Exception {
    addDeadLetterPartition();
    Map<String, Object> consumerProperties =
        KafkaTestUtils.consumerProps("recovered-" + UUID.randomUUID(), "false", broker);
    try (Consumer<byte[], byte[]> deadLetters =
        new DefaultKafkaConsumerFactory<>(
                consumerProperties, new ByteArrayDeserializer(), new ByteArrayDeserializer())
            .createConsumer()) {
      deadLetters.subscribe(List.of("gateway.recovery.test.DLT"));
      startProbe();
      send("second");

      ConsumerRecord<byte[], byte[]> failed =
          KafkaTestUtils.getSingleRecord(
              deadLetters, "gateway.recovery.test.DLT", Duration.ofSeconds(15));
      assertThat(failed.key()).containsExactly("first".getBytes(StandardCharsets.UTF_8));
      assertThat(failed.value()).containsExactly(1);
      assertThat(failed.partition()).isEqualTo(1);
      assertThat(probe.secondDelivered.await(10, TimeUnit.SECONDS)).isTrue();
      Awaitility.await()
          .atMost(Duration.ofSeconds(10))
          .untilAsserted(() -> assertThat(committedOffset()).isGreaterThanOrEqualTo(2));
    } finally {
      listeners.getListenerContainer("recovery-probe").stop();
    }
  }

  private void send(String key) throws Exception {
    kafka
        .send("gateway.recovery.test", 1, key.getBytes(StandardCharsets.UTF_8), new byte[] {1})
        .get(5, TimeUnit.SECONDS);
  }

  private void startProbe() {
    probe.reset();
    listeners.getListenerContainer("recovery-probe").start();
    ContainerTestUtils.waitForAssignment(listeners.getListenerContainer("recovery-probe"), 2);
  }

  private void addDeadLetterPartition() throws Exception {
    try (Admin admin =
        Admin.create(
            Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString()))) {
      admin
          .createPartitions(Map.of("gateway.recovery.test.DLT", NewPartitions.increaseTo(2)))
          .all()
          .get(5, TimeUnit.SECONDS);
    }
  }

  private long committedOffset() throws Exception {
    try (Admin admin =
        Admin.create(
            Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString()))) {
      var committed =
          admin
              .listConsumerGroupOffsets("gateway-recovery-test")
              .partitionsToOffsetAndMetadata()
              .get(5, TimeUnit.SECONDS);
      return committed.get(new TopicPartition("gateway.recovery.test", 1)).offset();
    }
  }

  static final class RecoveryProbe {
    private final List<String> keys = new CopyOnWriteArrayList<>();
    private CountDownLatch redelivered = new CountDownLatch(1);
    private CountDownLatch secondDelivered = new CountDownLatch(1);

    @KafkaListener(
        id = "recovery-probe",
        topics = "gateway.recovery.test",
        groupId = "gateway-recovery-test",
        autoStartup = "true")
    void receive(ConsumerRecord<byte[], byte[]> record) {
      String key = new String(record.key(), StandardCharsets.UTF_8);
      keys.add(key);
      if ("second".equals(key)) {
        secondDelivered.countDown();
        return;
      }
      if (keys.stream().filter("first"::equals).count() == 4) {
        redelivered.countDown();
      }
      throw new IllegalStateException("delivery remains unavailable");
    }

    void reset() {
      keys.clear();
      redelivered = new CountDownLatch(1);
      secondDelivered = new CountDownLatch(1);
    }
  }
}
