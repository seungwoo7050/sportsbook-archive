package com.sportsbook.risk.snapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.risk.counter.LimitType;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LimitSnapshotTest {
  @Test
  void combinesCommittedAndActiveCapacityWithCapturedOverrides() {
    LimitSnapshot snapshot = snapshot(new LimitSnapshot.Value(100, 25, 200L));
    LimitSnapshot.Value daily = snapshot.require(LimitType.STAKE_DAILY);

    assertThat(daily.current()).isEqualTo(125);
    assertThat(daily.effectiveLimit(999)).isEqualTo(200);
    assertThat(new LimitSnapshot.Value(1, 2, null).effectiveLimit(50)).isEqualTo(50);
  }

  @Test
  void requiresEveryDimensionAndDefersFactFailures() {
    assertThatThrownBy(
            () ->
                new LimitSnapshot(
                    Map.of(
                        LimitType.STAKE_DAILY,
                        SnapshotSlot.success(new LimitSnapshot.Value(0, 0, null)))))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("STAKE_WEEKLY");
    EnumMap<LimitType, SnapshotSlot<LimitSnapshot.Value>> values = values();
    values.put(LimitType.STAKE_MONTHLY, SnapshotSlot.failure("monthly unavailable"));
    LimitSnapshot snapshot = new LimitSnapshot(values);
    assertThatThrownBy(() -> snapshot.require(LimitType.STAKE_MONTHLY))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("monthly unavailable");
  }

  private static LimitSnapshot snapshot(LimitSnapshot.Value value) {
    EnumMap<LimitType, SnapshotSlot<LimitSnapshot.Value>> values = values();
    values.put(LimitType.STAKE_DAILY, SnapshotSlot.success(value));
    return new LimitSnapshot(values);
  }

  private static EnumMap<LimitType, SnapshotSlot<LimitSnapshot.Value>> values() {
    EnumMap<LimitType, SnapshotSlot<LimitSnapshot.Value>> values = new EnumMap<>(LimitType.class);
    for (LimitType type : LimitType.values()) {
      values.put(type, SnapshotSlot.success(new LimitSnapshot.Value(0, 0, null)));
    }
    return values;
  }
}
