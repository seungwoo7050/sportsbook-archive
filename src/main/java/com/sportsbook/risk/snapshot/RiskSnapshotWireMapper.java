package com.sportsbook.risk.snapshot;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.sportsbook.protocol.value.SelectionId;
import com.sportsbook.risk.counter.LimitType;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Strictly decodes one precision-safe snapshot script response. */
@Component
public final class RiskSnapshotWireMapper {
  private static final Set<String> LIMIT_NAMES =
      java.util.Arrays.stream(LimitType.values())
          .map(Enum::name)
          .collect(java.util.stream.Collectors.toUnmodifiableSet());

  private final ObjectReader reader;
  private final RiskSnapshotSlotMapper slots = new RiskSnapshotSlotMapper();

  public RiskSnapshotWireMapper(ObjectMapper mapper) {
    Objects.requireNonNull(mapper, "mapper");
    this.reader =
        mapper
            .copy()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .readerFor(RiskSnapshotWire.class);
  }

  public Decoded map(String raw, List<SelectionId> expectedSelections) {
    Objects.requireNonNull(expectedSelections, "expectedSelections");
    RiskSnapshotWire wire = read(raw);
    if (!"1".equals(wire.version()) || wire.limits() == null || wire.patterns() == null) {
      throw RiskWireNumbers.malformed("root");
    }
    if (!wire.limits().keySet().equals(LIMIT_NAMES)) {
      throw RiskWireNumbers.malformed("limits");
    }
    EnumMap<LimitType, SnapshotSlot<LimitSnapshot.Value>> limits = new EnumMap<>(LimitType.class);
    for (LimitType type : LimitType.values()) {
      limits.put(type, slots.limit(wire.limits().get(type.name()), "limits." + type.name()));
    }

    RiskSnapshotWire.PatternFacts facts = wire.patterns();
    if (facts.selections() == null || facts.selections().size() != expectedSelections.size()) {
      throw RiskWireNumbers.malformed("patterns.selections");
    }
    Map<SelectionId, SnapshotSlot<Long>> selections = new LinkedHashMap<>();
    for (int index = 0; index < expectedSelections.size(); index++) {
      SelectionId expected = expectedSelections.get(index);
      RiskSnapshotWire.SelectionFact fact = facts.selections().get(index);
      if (fact == null || !expected.value().toString().equals(fact.selectionId())) {
        throw RiskWireNumbers.malformed("patterns.selections[" + index + "]");
      }
      if (selections.put(expected, slots.count(fact.slot(), "selection." + expected.value()))
          != null) {
        throw RiskWireNumbers.malformed("duplicate selection");
      }
    }
    PatternSnapshot patterns =
        new PatternSnapshot(
            slots.count(facts.rapid(), "patterns.rapid"),
            slots.stakes(facts.stakes(), "patterns.stakes"),
            selections);
    return new Decoded(
        new RiskSnapshot(new LimitSnapshot(limits), patterns),
        RiskWireNumbers.exact(wire.expired(), "expired"));
  }

  private RiskSnapshotWire read(String raw) {
    if (raw == null) {
      throw RiskWireNumbers.malformed("payload");
    }
    try {
      return reader.readValue(raw);
    } catch (Exception failure) {
      throw new IllegalStateException("malformed snapshot payload", failure);
    }
  }

  public record Decoded(RiskSnapshot snapshot, long expired) {}
}
