package com.sportsbook.admin.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.admin.event.AdminActionRecorded;
import com.sportsbook.admin.security.AdminRole;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(AdminActionPublisherBrokerTest.TestConfiguration.class)
@EmbeddedKafka(topics = "admin.action", partitions = 1)
class AdminActionPublisherBrokerTest {

  @Autowired private EmbeddedKafkaBroker broker;

  @Test
  void sendsAnAvroTerminalEventKeyedByActor() throws IOException {
    var configuration = new AuditKafkaConfiguration(broker.getBrokersAsString());
    var producerFactory = configuration.auditProducerFactory();
    var template = configuration.auditKafkaTemplate(producerFactory);
    var publisher = new AdminActionPublisher(template, new SimpleMeterRegistry(), "admin.action");

    try {
      publisher.publish(terminal());
      template.flush();

      ConsumerRecord<String, byte[]> record = consumeOne();
      AdminActionRecorded event = decode(record.value());
      assertThat(record.key()).isEqualTo("operator-1");
      assertThat(event.getActionId()).isEqualTo("018f0000-0000-7000-8000-000000000093");
      assertThat(event.getOutcome()).isEqualTo("SUCCESS");
      assertThat(event.getHttpStatus()).isEqualTo(202);
    } finally {
      template.destroy();
      ((DefaultKafkaProducerFactory<String, byte[]>) producerFactory).destroy();
    }
  }

  private ConsumerRecord<String, byte[]> consumeOne() {
    Map<String, Object> properties =
        KafkaTestUtils.consumerProps("audit-publisher-test", "false", broker);
    properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    try (Consumer<String, byte[]> consumer =
        new DefaultKafkaConsumerFactory<>(
                properties, new StringDeserializer(), new ByteArrayDeserializer())
            .createConsumer()) {
      consumer.subscribe(java.util.List.of("admin.action"));
      return KafkaTestUtils.getSingleRecord(consumer, "admin.action", Duration.ofSeconds(10));
    }
  }

  private static AdminActionRecorded decode(byte[] value) throws IOException {
    var reader = new SpecificDatumReader<AdminActionRecorded>(AdminActionRecorded.class);
    return reader.read(null, DecoderFactory.get().binaryDecoder(value, null));
  }

  private static AuditTerminalRecord terminal() {
    Instant started = Instant.parse("2026-08-22T01:02:03Z");
    return new AuditTerminalRecord(
        UUID.fromString("018f0000-0000-7000-8000-000000000093"),
        "operator-1",
        AdminRole.ADMIN,
        "MARKET_CLOSE",
        "market-1",
        AuditOutcome.SUCCESS,
        202,
        "operator request",
        "trace-1",
        started,
        started.plusSeconds(1));
  }

  @Configuration(proxyBeanMethods = false)
  static class TestConfiguration {}
}
