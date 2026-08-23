package com.sportsbook.admin.audit;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
abstract class RealKafkaAuditFixture {

  static final String TOPIC = "admin.action";

  @Container
  static final KafkaContainer KAFKA =
      new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.0"))
          .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "false");

  private ProducerFactory<String, byte[]> producerFactory;
  private KafkaTemplate<String, byte[]> kafka;
  private AdminActionPublisher publisher;

  @BeforeAll
  static void createTopic() throws Exception {
    Map<String, Object> settings =
        Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
    try (Admin admin = Admin.create(settings)) {
      admin
          .createTopics(List.of(new NewTopic(TOPIC, 3, (short) 1)))
          .all()
          .get(10, TimeUnit.SECONDS);
    }
  }

  @BeforeEach
  void createPublisher() {
    AuditKafkaConfiguration configuration =
        new AuditKafkaConfiguration(KAFKA.getBootstrapServers());
    producerFactory = configuration.auditProducerFactory();
    kafka = configuration.auditKafkaTemplate(producerFactory);
    publisher = new AdminActionPublisher(kafka, new SimpleMeterRegistry(), TOPIC);
  }

  @AfterEach
  void destroyPublisher() {
    kafka.destroy();
    ((DefaultKafkaProducerFactory<String, byte[]>) producerFactory).destroy();
  }

  void publish(AuditTerminalRecord record) {
    publisher.publish(record);
    kafka.flush();
  }

  ConsumerRecord<String, byte[]> consumeOne() {
    Map<String, Object> properties =
        KafkaTestUtils.consumerProps(
            KAFKA.getBootstrapServers(), "real-audit-" + UUID.randomUUID(), "false");
    properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    properties.put(ConsumerConfig.ALLOW_AUTO_CREATE_TOPICS_CONFIG, false);
    try (Consumer<String, byte[]> consumer =
        new DefaultKafkaConsumerFactory<>(
                properties, new StringDeserializer(), new ByteArrayDeserializer())
            .createConsumer()) {
      consumer.subscribe(List.of(TOPIC));
      return KafkaTestUtils.getSingleRecord(consumer, TOPIC, Duration.ofSeconds(10));
    }
  }
}
