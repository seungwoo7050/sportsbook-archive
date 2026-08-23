package com.sportsbook.orchestration.fixture;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DecoderFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FixtureSchemaCompatibilityTest {
  private static final String EVENT_ID = "00000000-0000-0000-0000-0000000000ab";
  private static final String BET_ID = "00000000-0000-0000-0000-0000000000cd";
  private static final String USER_ID = "00000000-0000-0000-0000-000000000001";

  @TempDir Path temporaryDirectory;

  @Test
  void encodesEveryLockedSharedSchema() throws Exception {
    Map<FixtureType, String> fixtures =
        Map.of(
            FixtureType.EVENT_LIFECYCLE,
            """
            {"eventId":"%s","status":"FINISHED",
             "occurredAt":1700000000000,"scheduledStartAt":1699990000000}
            """
                .formatted(EVENT_ID),
            FixtureType.MATCH_RESULT,
            """
            {"eventId":"%s","score":"2-1","finalStatus":"COMPLETED",
             "resultDetail":{"selection":"WON"},"settledAt":1700000001000}
            """
                .formatted(EVENT_ID),
            FixtureType.BET_SETTLED,
            """
            {"betId":"%s","userId":"%s","eventId":"%s","result":"WON",
             "stake":{"amount":1000,"currency":"KRW"},
             "payout":{"amount":2000,"currency":"KRW"},
             "settledAt":1700000002000,"resultDetail":null}
            """
                .formatted(BET_ID, USER_ID, EVENT_ID),
            FixtureType.BET_RESOLUTION_REVISED,
            """
            {"revisionId":"00000000-0000-0000-0000-0000000000ef",
             "revisionNumber":1,"betId":"%s","userId":"%s","eventId":"%s",
             "previousResult":"LOST","newResult":"WON",
             "previousPayout":{"amount":0,"currency":"KRW"},
             "newPayout":{"amount":2000,"currency":"KRW"},
             "sourceResultSettledAt":1700000003000,"revisedAt":1700000004000}
            """
                .formatted(BET_ID, USER_ID, EVENT_ID));

    for (Map.Entry<FixtureType, String> fixture : fixtures.entrySet()) {
      FixtureType type = fixture.getKey();
      Path json = Files.writeString(temporaryDirectory.resolve(type.name() + ".json"), fixture.getValue());
      FixtureEncoder.EncodedFixture encoded = FixtureEncoder.encode(type, json);
      GenericRecord decoded =
          new GenericDatumReader<GenericRecord>(type.schema())
              .read(
                  null,
                  DecoderFactory.get()
                      .directBinaryDecoder(new ByteArrayInputStream(encoded.payload()), null));

      assertEquals(encoded.key(), type.key(decoded));
    }
  }
}
