package com.sportsbook.risk.snapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class RiskSnapshotSlotMapperTest {
  private final RiskSnapshotSlotMapper mapper = new RiskSnapshotSlotMapper();

  @Test
  void mapsSuccessfulLimitAndPatternFacts() {
    SnapshotSlot<LimitSnapshot.Value> limit =
        mapper.limit(new RiskSnapshotWire.LimitSlot(true, "10", "4", "20", null), "daily");

    assertThat(limit.valueOrThrow()).isEqualTo(new LimitSnapshot.Value(10, 4, 20L));
    assertThat(
            mapper
                .count(new RiskSnapshotWire.FactSlot(true, "3", null, null), "rapid")
                .valueOrThrow())
        .isEqualTo(3);
    assertThat(
            mapper
                .stakes(new RiskSnapshotWire.FactSlot(true, "10,20", null, null), "stakes")
                .valueOrThrow())
        .containsExactly(10L, 20L);
    assertThat(
            mapper
                .stakes(new RiskSnapshotWire.FactSlot(true, "", null, null), "stakes")
                .valueOrThrow())
        .isEmpty();
  }

  @Test
  void preservesDeferredFailures() {
    SnapshotSlot<Long> failure =
        mapper.count(new RiskSnapshotWire.FactSlot(false, null, null, "unavailable"), "rapid");

    assertThatThrownBy(failure::valueOrThrow)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("unavailable");
  }

  @Test
  void rejectsAmbiguousOrInvalidShapes() {
    assertThatThrownBy(
            () ->
                mapper.limit(
                    new RiskSnapshotWire.LimitSlot(false, "1", null, null, "failed"), "daily"))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(
            () ->
                mapper.count(new RiskSnapshotWire.FactSlot(true, "1", List.of("1"), null), "rapid"))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(
            () -> mapper.stakes(new RiskSnapshotWire.FactSlot(true, "10,0", null, null), "stakes"))
        .isInstanceOf(IllegalStateException.class);
  }
}
