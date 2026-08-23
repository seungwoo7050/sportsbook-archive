package com.sportsbook.orchestration.fixture;

import com.sportsbook.protocol.event.BetResolutionRevised;
import com.sportsbook.protocol.event.BetSettled;
import com.sportsbook.protocol.event.EventLifecycle;
import com.sportsbook.protocol.event.MatchResult;
import java.util.Arrays;
import java.util.UUID;
import org.apache.avro.Schema;
import org.apache.avro.SchemaNormalization;
import org.apache.avro.generic.GenericRecord;

public enum FixtureType {
  EVENT_LIFECYCLE(
      "EventLifecycle", EventLifecycle.getClassSchema(), "event.lifecycle", "eventId", 0xe47d6dbd952bc721L),
  MATCH_RESULT(
      "MatchResult", MatchResult.getClassSchema(), "match.result", "eventId", 0x3f39fbc4bbfea727L),
  BET_SETTLED(
      "BetSettled", BetSettled.getClassSchema(), "bet.settled.v1", "eventId", 0x113bc9d5037a850cL),
  BET_RESOLUTION_REVISED(
      "BetResolutionRevised",
      BetResolutionRevised.getClassSchema(),
      "bet.resolution.revised.v1",
      "betId",
      0xb05cdf4b95651059L);

  private final String cliName;
  private final Schema schema;
  private final String topic;
  private final String keyField;
  private final long fingerprint;

  FixtureType(String cliName, Schema schema, String topic, String keyField, long fingerprint) {
    this.cliName = cliName;
    this.schema = schema;
    this.topic = topic;
    this.keyField = keyField;
    this.fingerprint = fingerprint;
    if (SchemaNormalization.parsingFingerprint64(schema) != fingerprint) {
      throw new IllegalStateException(cliName + " schema fingerprint mismatch");
    }
  }

  public static FixtureType fromCliName(String value) {
    return Arrays.stream(values())
        .filter(type -> type.cliName.equals(value))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("unsupported fixture type: " + value));
  }

  public Schema schema() {
    return schema;
  }

  public String topic() {
    return topic;
  }

  public String key(GenericRecord record) {
    String key = String.valueOf(record.get(keyField));
    if (!UUID.fromString(key).toString().equals(key)) {
      throw new IllegalArgumentException(keyField + " must be a canonical UUID");
    }
    return key;
  }

  public String fingerprint() {
    return "%016x".formatted(fingerprint);
  }
}
