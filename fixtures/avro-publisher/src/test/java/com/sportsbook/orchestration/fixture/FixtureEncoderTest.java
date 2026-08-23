package com.sportsbook.orchestration.fixture;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DecoderFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FixtureEncoderTest {
  private static final String EVENT_ID = "00000000-0000-0000-0000-0000000000ab";
  private static final String EVENT_JSON =
      """
      {
        "eventId": "%s",
        "status": "FINISHED",
        "occurredAt": 1700000000000,
        "scheduledStartAt": 1699990000000
      }
      """
          .formatted(EVENT_ID);

  @TempDir Path temporaryDirectory;

  @Test
  void producesDeterministicRawAvroWithoutTrailingBytes() throws Exception {
    Path input = write("event.json", EVENT_JSON);

    FixtureEncoder.EncodedFixture first =
        FixtureEncoder.encode(FixtureType.EVENT_LIFECYCLE, input);
    FixtureEncoder.EncodedFixture second =
        FixtureEncoder.encode(FixtureType.EVENT_LIFECYCLE, input);

    assertEquals(EVENT_ID, first.key());
    assertArrayEquals(first.payload(), second.payload());

    ByteArrayInputStream bytes = new ByteArrayInputStream(first.payload());
    GenericRecord decoded =
        new GenericDatumReader<GenericRecord>(FixtureType.EVENT_LIFECYCLE.schema())
            .read(null, DecoderFactory.get().directBinaryDecoder(bytes, null));
    assertEquals(EVENT_ID, decoded.get("eventId").toString());
    assertEquals(0, bytes.available());
  }

  @Test
  void rejectsMoreThanOneJsonRecord() throws Exception {
    Path input = write("two-events.json", EVENT_JSON + EVENT_JSON);

    assertThrows(
        IllegalArgumentException.class,
        () -> FixtureEncoder.encode(FixtureType.EVENT_LIFECYCLE, input));
  }

  @Test
  void returnsDefensivePayloadCopies() throws Exception {
    FixtureEncoder.EncodedFixture encoded =
        FixtureEncoder.encode(FixtureType.EVENT_LIFECYCLE, write("event.json", EVENT_JSON));
    byte expected = encoded.payload()[0];

    byte[] callerCopy = encoded.payload();
    callerCopy[0] = (byte) (callerCopy[0] + 1);

    assertEquals(expected, encoded.payload()[0]);
  }

  private Path write(String name, String contents) throws Exception {
    return Files.writeString(temporaryDirectory.resolve(name), contents);
  }
}
