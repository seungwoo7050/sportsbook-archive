package com.sportsbook.orchestration.fixture;

import java.io.IOException;
import java.nio.file.Path;

public record FixtureArguments(String bootstrapServers, FixtureRecord fixture) {
  public FixtureArguments {
    if (bootstrapServers == null || bootstrapServers.isBlank()) {
      throw new IllegalArgumentException("bootstrap servers must not be blank");
    }
  }

  public static FixtureArguments parse(String[] arguments) throws IOException {
    if (arguments.length == 3 && arguments[0].equals("poison")) {
      return new FixtureArguments(
          arguments[1], FixtureRecord.poisonMatchResult(arguments[2]));
    }
    if ((arguments.length == 4 || arguments.length == 5)
        && arguments[0].equals("publish")) {
      Integer partition = arguments.length == 5 ? parsePartition(arguments[4]) : null;
      FixtureRecord fixture =
          FixtureRecord.fromJson(
              FixtureType.fromCliName(arguments[2]), Path.of(arguments[3]), partition);
      return new FixtureArguments(arguments[1], fixture);
    }
    throw new IllegalArgumentException(
        "usage: publish <bootstrap> <type> <json> [partition]"
            + " | poison <bootstrap> <event-id>");
  }

  private static int parsePartition(String value) {
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException("partition must be an integer", exception);
    }
  }
}
