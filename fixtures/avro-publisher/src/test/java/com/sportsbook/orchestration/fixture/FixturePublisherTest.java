package com.sportsbook.orchestration.fixture;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import java.util.Properties;
import java.util.Set;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.Cluster;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;

class FixturePublisherTest {
  private static final String EVENT_ID = "00000000-0000-0000-0000-0000000000ab";

  @Test
  void requiresAcknowledgedIdempotentPublication() {
    Properties properties = FixturePublisher.producerProperties("kafka:9092");

    assertEquals("kafka:9092", properties.get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG));
    assertEquals("all", properties.get(ProducerConfig.ACKS_CONFIG));
    assertEquals("true", properties.get(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG));
    assertEquals(
        StringSerializer.class.getName(),
        properties.get(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG));
    assertEquals(
        ByteArraySerializer.class.getName(),
        properties.get(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG));
  }

  @Test
  void waitsForBrokerMetadataAndEmitsASecretFreeReceipt() throws Exception {
    MockProducer<String, byte[]> producer =
        new MockProducer<>(cluster(), true, new StringSerializer(), new ByteArraySerializer());
    FixtureRecord poison = FixtureRecord.poisonMatchResult(EVENT_ID);

    FixturePublisher.PublicationReceipt receipt = FixturePublisher.publish(producer, poison);

    ProducerRecord<String, byte[]> sent = producer.history().get(0);
    assertEquals("match.result", sent.topic());
    assertEquals(2, sent.partition());
    assertEquals(EVENT_ID, sent.key());
    assertArrayEquals(new byte[] {(byte) 0x80}, sent.value());
    assertEquals("match.result", receipt.topic());
    assertEquals(2, receipt.partition());
    assertEquals(0, receipt.offset());
    assertEquals(
        "76be8b528d0075f7aae98d6fa57a6d3c83ae480a8469e668d7b0af968995ac71",
        receipt.sha256());
    assertFalse(receipt.format().contains("bootstrap"));
    assertFalse(receipt.format().contains("payload"));
  }

  private static Cluster cluster() {
    Node broker = new Node(0, "localhost", 9092);
    List<PartitionInfo> partitions =
        java.util.stream.IntStream.range(0, 3)
            .mapToObj(
                partition ->
                    new PartitionInfo(
                        "match.result",
                        partition,
                        broker,
                        new Node[] {broker},
                        new Node[] {broker}))
            .toList();
    return new Cluster("fixture", List.of(broker), partitions, Set.of(), Set.of());
  }
}
