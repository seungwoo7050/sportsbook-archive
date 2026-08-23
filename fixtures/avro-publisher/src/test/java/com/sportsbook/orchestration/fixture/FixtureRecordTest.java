package com.sportsbook.orchestration.fixture;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FixtureRecordTest {
  private static final String EVENT_ID = "00000000-0000-0000-0000-0000000000ab";

  @TempDir Path temporaryDirectory;

  @Test
  void derivesTopicKeyAndFingerprintFromTheLockedSchema() throws Exception {
    Path json =
        Files.writeString(
            temporaryDirectory.resolve("event.json"),
            """
            {
              "eventId": "%s",
              "status": "FINISHED",
              "occurredAt": 1700000000000,
              "scheduledStartAt": 1699990000000
            }
            """
                .formatted(EVENT_ID));

    FixtureRecord fixture = FixtureRecord.fromJson(FixtureType.EVENT_LIFECYCLE, json, 1);
    ProducerRecord<String, byte[]> record = fixture.producerRecord();

    assertEquals("event.lifecycle", record.topic());
    assertEquals(EVENT_ID, record.key());
    assertEquals(1, record.partition());
    assertEquals("e47d6dbd952bc721", fixture.fingerprint());
    assertArrayEquals(fixture.payload(), record.value());
  }

  @Test
  void permitsBrokerPartitioningButRejectsOutOfRangePartitions() throws Exception {
    FixtureRecord fixture =
        new FixtureRecord("match.result", EVENT_ID, null, new byte[] {1}, "fingerprint");

    assertNull(fixture.producerRecord().partition());
    assertThrows(
        IllegalArgumentException.class,
        () -> new FixtureRecord("match.result", EVENT_ID, -1, new byte[] {1}, "fingerprint"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new FixtureRecord("match.result", EVENT_ID, 3, new byte[] {1}, "fingerprint"));
  }

  @Test
  void fixesPoisonRecordToMatchResultPartitionTwo() {
    FixtureRecord poison = FixtureRecord.poisonMatchResult(EVENT_ID);

    assertEquals("match.result", poison.topic());
    assertEquals(EVENT_ID, poison.key());
    assertEquals(2, poison.partition());
    assertArrayEquals(new byte[] {(byte) 0x80}, poison.payload());
    assertEquals("malformed", poison.fingerprint());
    assertThrows(
        IllegalArgumentException.class,
        () -> FixtureRecord.poisonMatchResult(EVENT_ID.toUpperCase()));
  }
}
