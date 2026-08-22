package com.sportsbook.settlement.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class SettlementMetricsTest {

  @Test
  void recordsOnlyBoundedFlowAndOutcomeLabels() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    SettlementMetrics metrics = new SettlementMetrics(registry);
    var sample = metrics.start();

    metrics.count("base_result", "succeeded", 2);
    metrics.stop(sample, "base_result");

    assertThat(
            registry
                .counter(
                    SettlementMetrics.OPERATIONS, "flow", "base_result", "outcome", "succeeded")
                .count())
        .isEqualTo(2);
    assertThat(registry.timer(SettlementMetrics.DURATION, "flow", "base_result").count()).isOne();
    assertThatThrownBy(() -> metrics.count("base_result", "secret value"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> metrics.count("base_result", "failed", -1))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
