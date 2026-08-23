package com.sportsbook.orchestration.fixture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FixtureArgumentsTest {
  private static final String EVENT_ID = "00000000-0000-0000-0000-0000000000ab";

  @TempDir Path temporaryDirectory;

  @Test
  void parsesPublishWithOptionalLockedPartition() throws Exception {
    Path json = writeEvent();

    FixtureArguments automatic =
        FixtureArguments.parse(
            new String[] {"publish", "kafka:9092", "EventLifecycle", json.toString()});
    FixtureArguments explicit =
        FixtureArguments.parse(
            new String[] {"publish", "kafka:9092", "EventLifecycle", json.toString(), "2"});

    assertEquals("kafka:9092", automatic.bootstrapServers());
    assertEquals("event.lifecycle", automatic.fixture().topic());
    assertNull(automatic.fixture().partition());
    assertEquals(2, explicit.fixture().partition());
  }

  @Test
  void parsesOnlyTheFixedPoisonCommand() throws Exception {
    FixtureArguments arguments =
        FixtureArguments.parse(new String[] {"poison", "kafka:9092", EVENT_ID});

    assertEquals("match.result", arguments.fixture().topic());
    assertEquals(2, arguments.fixture().partition());
  }

  @Test
  void rejectsOpenEndedOrMalformedCommands() {
    assertThrows(
        IllegalArgumentException.class,
        () -> FixtureArguments.parse(new String[] {"raw", "kafka:9092", "topic", "key"}));
    assertThrows(
        IllegalArgumentException.class,
        () -> FixtureArguments.parse(new String[] {"poison", "", EVENT_ID}));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            FixtureArguments.parse(
                new String[] {
                  "publish", "kafka:9092", "EventLifecycle", "missing.json", "partition"
                }));
  }

  private Path writeEvent() throws Exception {
    return Files.writeString(
        temporaryDirectory.resolve("event.json"),
        """
        {"eventId":"%s","status":"FINISHED",
         "occurredAt":1700000000000,"scheduledStartAt":1699990000000}
        """
            .formatted(EVENT_ID));
  }
}
