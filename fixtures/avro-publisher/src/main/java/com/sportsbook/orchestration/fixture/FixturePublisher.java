package com.sportsbook.orchestration.fixture;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;

public final class FixturePublisher {
  private FixturePublisher() {}

  public static void main(String[] arguments) throws Exception {
    FixtureArguments parsed = FixtureArguments.parse(arguments);
    try (Producer<String, byte[]> producer =
        new KafkaProducer<>(producerProperties(parsed.bootstrapServers()))) {
      System.out.println(publish(producer, parsed.fixture()).format());
    }
  }

  static PublicationReceipt publish(Producer<String, byte[]> producer, FixtureRecord fixture)
      throws Exception {
    RecordMetadata metadata = producer.send(fixture.producerRecord()).get(30, TimeUnit.SECONDS);
    return new PublicationReceipt(
        metadata.topic(),
        fixture.key(),
        metadata.partition(),
        metadata.offset(),
        sha256(fixture.payload()),
        fixture.fingerprint());
  }

  static Properties producerProperties(String bootstrapServers) {
    Properties properties = new Properties();
    properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    properties.put(ProducerConfig.ACKS_CONFIG, "all");
    properties.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
    properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    properties.put(
        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
    return properties;
  }

  private static String sha256(byte[] payload) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  record PublicationReceipt(
      String topic, String key, int partition, long offset, String sha256, String fingerprint) {
    String format() {
      return "topic=%s\tkey=%s\tpartition=%d\toffset=%d\tsha256=%s\tfingerprint=%s"
          .formatted(topic, key, partition, offset, sha256, fingerprint);
    }
  }
}
