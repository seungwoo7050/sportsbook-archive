package com.sportsbook.risk.snapshot;

import com.sportsbook.protocol.value.SelectionId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Atomic confirmed and active facts consumed by deterministic pattern rules. */
public record PatternSnapshot(
    SnapshotSlot<Long> recentBetCount,
    SnapshotSlot<List<Long>> recentStakes,
    Map<SelectionId, SnapshotSlot<Long>> selectionCounts) {
  public PatternSnapshot {
    Objects.requireNonNull(recentBetCount, "recentBetCount");
    Objects.requireNonNull(recentStakes, "recentStakes");
    Objects.requireNonNull(selectionCounts, "selectionCounts");
    if (recentStakes.value() != null) {
      recentStakes = SnapshotSlot.success(List.copyOf(recentStakes.value()));
    }
    selectionCounts = Map.copyOf(new LinkedHashMap<>(selectionCounts));
  }

  public SnapshotSlot<Long> selectionCount(SelectionId selectionId) {
    SnapshotSlot<Long> slot =
        selectionCounts.get(Objects.requireNonNull(selectionId, "selectionId"));
    if (slot == null) {
      throw new IllegalArgumentException("selection is absent from snapshot");
    }
    return slot;
  }
}
