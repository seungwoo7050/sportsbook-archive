package com.sportsbook.orchestration.fixture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import java.util.Set;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.Test;

class FixtureTypeTest {
  private static final String UUID = "00000000-0000-0000-0000-0000000000ab";

  @Test
  void exposesOnlyLockedEventContracts() {
    Map<FixtureType, ExpectedContract> expected =
        Map.of(
            FixtureType.EVENT_LIFECYCLE,
            new ExpectedContract("EventLifecycle", "event.lifecycle", "e47d6dbd952bc721"),
            FixtureType.MATCH_RESULT,
            new ExpectedContract("MatchResult", "match.result", "3f39fbc4bbfea727"),
            FixtureType.BET_SETTLED,
            new ExpectedContract("BetSettled", "bet.settled.v1", "113bc9d5037a850c"),
            FixtureType.BET_RESOLUTION_REVISED,
            new ExpectedContract(
                "BetResolutionRevised", "bet.resolution.revised.v1", "b05cdf4b95651059"));

    assertEquals(expected.keySet(), Set.of(FixtureType.values()));
    expected.forEach(
        (type, contract) -> {
          assertEquals(type, FixtureType.fromCliName(contract.cliName()));
          assertEquals(contract.topic(), type.topic());
          assertEquals(contract.fingerprint(), type.fingerprint());
          assertEquals(UUID, type.key(recordWithKey(type, UUID)));
        });
  }

  @Test
  void rejectsUnsupportedTypeAndNonCanonicalKey() {
    assertThrows(IllegalArgumentException.class, () -> FixtureType.fromCliName("BetPlaced"));
    assertThrows(
        IllegalArgumentException.class,
        () -> FixtureType.MATCH_RESULT.key(recordWithKey(FixtureType.MATCH_RESULT, "not-a-uuid")));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            FixtureType.MATCH_RESULT.key(
                recordWithKey(FixtureType.MATCH_RESULT, UUID.toUpperCase())));
  }

  private static GenericRecord recordWithKey(FixtureType type, String key) {
    GenericRecord record = new GenericData.Record(type.schema());
    String field = type == FixtureType.BET_RESOLUTION_REVISED ? "betId" : "eventId";
    record.put(field, key);
    return record;
  }

  private record ExpectedContract(String cliName, String topic, String fingerprint) {}
}
