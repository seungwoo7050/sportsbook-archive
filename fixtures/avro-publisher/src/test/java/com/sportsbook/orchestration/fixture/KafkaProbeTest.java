package com.sportsbook.orchestration.fixture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KafkaProbeTest {
  private static final String EVENT_ID = "00000000-0000-0000-0000-0000000000ab";

  @TempDir Path temporaryDirectory;

  @Test
  void preservesMetadataHeadersAndDecodedAvro() throws Exception {
    Path fixture =
        Files.writeString(
            temporaryDirectory.resolve("result.json"),
            """
            {"eventId":"%s","score":"1-0","finalStatus":"COMPLETED",
             "resultDetail":{"selection":"WON"},"settledAt":1700000001000}
            """
                .formatted(EVENT_ID));
    byte[] payload = FixtureEncoder.encode(FixtureType.MATCH_RESULT, fixture).payload();
    ConsumerRecord<byte[], byte[]> record =
        new ConsumerRecord<>(
            "match.result.DLT",
            2,
            41,
            EVENT_ID.getBytes(StandardCharsets.UTF_8),
            payload);
    record
        .headers()
        .add("kafka_dlt-original-partition", ByteBuffer.allocate(4).putInt(2).array());
    Path schema =
        Files.writeString(
            temporaryDirectory.resolve("MatchResult.avsc"),
            FixtureType.MATCH_RESULT.schema().toString());

    JsonNode output = new ObjectMapper().readTree(KafkaProbe.format(record, schema));

    assertEquals("match.result.DLT", output.get("topic").asText());
    assertEquals(2, output.get("partition").asInt());
    assertEquals(41, output.get("offset").asLong());
    assertEquals(EVENT_ID, output.get("key").asText());
    assertEquals(Base64.getEncoder().encodeToString(payload), output.get("valueBase64").asText());
    assertEquals(EVENT_ID, output.at("/avro/eventId").asText());
    assertEquals(
        Base64.getEncoder()
            .encodeToString(ByteBuffer.allocate(4).putInt(2).array()),
        output.at("/headers/kafka_dlt-original-partition/0").asText());
  }

  @Test
  void rejectsTrailingAvroBytes() throws Exception {
    Path fixture =
        Files.writeString(
            temporaryDirectory.resolve("event.json"),
            """
            {"eventId":"%s","status":"FINISHED",
             "occurredAt":1700000000000,"scheduledStartAt":1699990000000}
            """
                .formatted(EVENT_ID));
    byte[] encoded = FixtureEncoder.encode(FixtureType.EVENT_LIFECYCLE, fixture).payload();
    byte[] trailing = Arrays.copyOf(encoded, encoded.length + 1);
    ConsumerRecord<byte[], byte[]> record =
        new ConsumerRecord<>("event.lifecycle", 0, 0, EVENT_ID.getBytes(), trailing);
    Path schema =
        Files.writeString(
            temporaryDirectory.resolve("EventLifecycle.avsc"),
            FixtureType.EVENT_LIFECYCLE.schema().toString());

    assertThrows(IllegalArgumentException.class, () -> KafkaProbe.format(record, schema));
  }
}
