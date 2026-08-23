package com.sportsbook.orchestration.fixture;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DecoderFactory;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;

public final class KafkaProbe {
  private static final ObjectMapper JSON = new ObjectMapper();

  private KafkaProbe() {}

  public static void main(String[] arguments) throws Exception {
    if (arguments.length < 4 || arguments.length > 5) {
      throw new IllegalArgumentException(
          "usage: <bootstrap> <topic> <partition> <offset> [avro-schema]");
    }
    int partition = Integer.parseInt(arguments[2]);
    long offset = Long.parseLong(arguments[3]);
    if (partition < 0 || partition > 2 || offset < 0) {
      throw new IllegalArgumentException("partition or offset is out of range");
    }
    Path schema = arguments.length == 5 ? Path.of(arguments[4]) : null;
    System.out.println(read(arguments[0], arguments[1], partition, offset, schema));
  }

  static String read(String bootstrap, String topic, int partition, long offset, Path schema)
      throws Exception {
    Properties properties = new Properties();
    properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
    properties.put(ConsumerConfig.GROUP_ID_CONFIG, "fixture-probe-" + UUID.randomUUID());
    properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
    properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
    properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
    try (KafkaConsumer<byte[], byte[]> consumer = new KafkaConsumer<>(properties)) {
      TopicPartition target = new TopicPartition(topic, partition);
      consumer.assign(List.of(target));
      consumer.seek(target, offset);
      long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
      while (System.nanoTime() < deadline) {
        for (ConsumerRecord<byte[], byte[]> record : consumer.poll(Duration.ofMillis(250))) {
          if (record.offset() == offset) {
            return format(record, schema);
          }
        }
      }
      throw new IllegalStateException("record did not arrive before the probe deadline");
    }
  }

  static String format(ConsumerRecord<byte[], byte[]> record, Path schemaPath) throws Exception {
    Map<String, Object> output = new LinkedHashMap<>();
    output.put("topic", record.topic());
    output.put("partition", record.partition());
    output.put("offset", record.offset());
    output.put("key", new String(record.key(), StandardCharsets.UTF_8));
    output.put("valueBase64", Base64.getEncoder().encodeToString(record.value()));
    output.put("valueSha256", java.util.HexFormat.of().formatHex(
        MessageDigest.getInstance("SHA-256").digest(record.value())));
    Map<String, List<String>> headers = new LinkedHashMap<>();
    record.headers().forEach(
        header -> headers.computeIfAbsent(header.key(), ignored -> new ArrayList<>())
            .add(Base64.getEncoder().encodeToString(header.value())));
    output.put("headers", headers);
    if (schemaPath != null) {
      Schema schema = new Schema.Parser().parse(schemaPath.toFile());
      ByteArrayInputStream input = new ByteArrayInputStream(record.value());
      GenericRecord decoded = new GenericDatumReader<GenericRecord>(schema)
          .read(null, DecoderFactory.get().directBinaryDecoder(input, null));
      if (input.available() != 0) {
        throw new IllegalArgumentException("Avro payload has trailing bytes");
      }
      output.put("avro", JSON.readTree(GenericData.get().toString(decoded)));
    }
    return JSON.writeValueAsString(output);
  }
}
