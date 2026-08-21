package com.sportsbook.risk.snapshot;

import java.util.List;

/** Validates the mutually exclusive success and failure shapes of snapshot facts. */
final class RiskSnapshotSlotMapper {
  SnapshotSlot<LimitSnapshot.Value> limit(RiskSnapshotWire.LimitSlot wire, String name) {
    if (wire == null || wire.ok() == null) {
      throw RiskWireNumbers.malformed(name);
    }
    if (!wire.ok()) {
      requireFailure(wire.error(), name);
      if (wire.committed() != null || wire.active() != null || wire.override() != null) {
        throw RiskWireNumbers.malformed(name);
      }
      return SnapshotSlot.failure(wire.error());
    }
    requireNoError(wire.error(), name);
    long committed = RiskWireNumbers.exact(wire.committed(), name + ".committed");
    long active = RiskWireNumbers.exact(wire.active(), name + ".active");
    Long override =
        wire.override() == null ? null : RiskWireNumbers.exact(wire.override(), name + ".override");
    return SnapshotSlot.success(new LimitSnapshot.Value(committed, active, override));
  }

  SnapshotSlot<Long> count(RiskSnapshotWire.FactSlot wire, String name) {
    if (wire == null || wire.ok() == null) {
      throw RiskWireNumbers.malformed(name);
    }
    if (!wire.ok()) {
      requireFailure(wire.error(), name);
      if (wire.value() != null || wire.values() != null) {
        throw RiskWireNumbers.malformed(name);
      }
      return SnapshotSlot.failure(wire.error());
    }
    requireNoError(wire.error(), name);
    if (wire.values() != null) {
      throw RiskWireNumbers.malformed(name);
    }
    return SnapshotSlot.success(RiskWireNumbers.exact(wire.value(), name + ".value"));
  }

  SnapshotSlot<List<Long>> stakes(RiskSnapshotWire.FactSlot wire, String name) {
    if (wire == null || wire.ok() == null) {
      throw RiskWireNumbers.malformed(name);
    }
    if (!wire.ok()) {
      requireFailure(wire.error(), name);
      if (wire.value() != null || wire.values() != null) {
        throw RiskWireNumbers.malformed(name);
      }
      return SnapshotSlot.failure(wire.error());
    }
    requireNoError(wire.error(), name);
    if (wire.value() == null || wire.values() != null) {
      throw RiskWireNumbers.malformed(name);
    }
    if (wire.value().isEmpty()) {
      return SnapshotSlot.success(List.of());
    }
    String[] encoded = wire.value().split(",", -1);
    List<Long> values =
        java.util.stream.IntStream.range(0, encoded.length)
            .mapToObj(index -> positive(encoded[index], name + ".values[" + index + "]"))
            .toList();
    return SnapshotSlot.success(values);
  }

  private static long positive(String raw, String name) {
    long value = RiskWireNumbers.exact(raw, name);
    if (value == 0) {
      throw RiskWireNumbers.malformed(name);
    }
    return value;
  }

  private static void requireFailure(String error, String name) {
    if (error == null || error.isBlank()) {
      throw RiskWireNumbers.malformed(name);
    }
  }

  private static void requireNoError(String error, String name) {
    if (error != null) {
      throw RiskWireNumbers.malformed(name);
    }
  }
}
