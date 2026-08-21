package com.sportsbook.risk.snapshot;

import com.sportsbook.risk.counter.LimitType;
import com.sportsbook.risk.policy.SafeRedisNumber;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Captured committed, active, and override values for all rolling dimensions. */
public record LimitSnapshot(Map<LimitType, SnapshotSlot<Value>> values) {
  public LimitSnapshot {
    Objects.requireNonNull(values, "values");
    EnumMap<LimitType, SnapshotSlot<Value>> copy = new EnumMap<>(LimitType.class);
    copy.putAll(values);
    for (LimitType type : LimitType.values()) {
      Objects.requireNonNull(copy.get(type), "missing limit snapshot: " + type);
    }
    values = Map.copyOf(copy);
  }

  public Value require(LimitType type) {
    return values.get(Objects.requireNonNull(type, "type")).valueOrThrow();
  }

  public record Value(long committed, long active, Long override) {
    public Value {
      SafeRedisNumber.requireNonNegative(committed, "committed");
      SafeRedisNumber.requireNonNegative(active, "active");
      if (override != null) {
        SafeRedisNumber.requireNonNegative(override, "override");
      }
    }

    public long current() {
      return SafeRedisNumber.add(committed, active, "current risk capacity");
    }

    public long effectiveLimit(long deployedDefault) {
      SafeRedisNumber.requireNonNegative(deployedDefault, "deployed default");
      return override == null ? deployedDefault : override;
    }
  }
}
