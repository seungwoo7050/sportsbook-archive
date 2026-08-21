package com.sportsbook.risk.snapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.protocol.value.SelectionId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PatternSnapshotTest {
  private static final SelectionId SELECTION = SelectionId.of(new UUID(0, 3));

  @Test
  void retainsSuccessfulFactsWithoutAliasingCollections() {
    List<Long> stakes = new ArrayList<>(List.of(10L, 20L));
    PatternSnapshot snapshot =
        new PatternSnapshot(
            SnapshotSlot.success(3L),
            SnapshotSlot.success(stakes),
            Map.of(SELECTION, SnapshotSlot.success(2L)));
    stakes.clear();

    assertThat(snapshot.recentBetCount().valueOrThrow()).isEqualTo(3);
    assertThat(snapshot.recentStakes().valueOrThrow()).containsExactly(10L, 20L);
    assertThat(snapshot.selectionCount(SELECTION).valueOrThrow()).isEqualTo(2);
    assertThatThrownBy(() -> snapshot.recentStakes().valueOrThrow().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void defersRedisFailuresUntilTheirFactIsConsumed() {
    PatternSnapshot snapshot =
        new PatternSnapshot(
            SnapshotSlot.failure("rapid unavailable"),
            SnapshotSlot.success(List.of()),
            Map.of(SELECTION, SnapshotSlot.failure("selection unavailable")));

    assertThat(snapshot.recentBetCount().failure()).contains("rapid unavailable");
    assertThatThrownBy(snapshot.recentBetCount()::valueOrThrow)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("rapid unavailable");
    assertThatThrownBy(() -> snapshot.selectionCount(SelectionId.of(new UUID(0, 4))))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> SnapshotSlot.success(null)).isInstanceOf(NullPointerException.class);
  }
}
