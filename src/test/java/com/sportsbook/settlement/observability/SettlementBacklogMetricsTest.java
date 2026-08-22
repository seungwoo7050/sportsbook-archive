package com.sportsbook.settlement.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class SettlementBacklogMetricsTest {

  @Test
  void exposesOnlyBoundedDurableWorkKinds() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.queryForObject("select count(*) from bet where status = 'PENDING'", Long.class))
        .thenReturn(11L);
    when(jdbc.queryForObject(
            "select count(*) from settlement_revision where state = 'BLOCKED'", Long.class))
        .thenReturn(3L);
    when(jdbc.queryForObject(
            "select count(*) from settlement_revision where state = 'EXHAUSTED'", Long.class))
        .thenReturn(2L);
    when(jdbc.queryForObject(
            "select count(*) from outbox_event where published_at is null", Long.class))
        .thenReturn(5L);
    SimpleMeterRegistry registry = new SimpleMeterRegistry();

    new SettlementBacklogMetrics(jdbc).bindTo(registry);

    assertThat(value(registry, "pending_bets")).isEqualTo(11);
    assertThat(value(registry, "blocked_revisions")).isEqualTo(3);
    assertThat(value(registry, "exhausted_revisions")).isEqualTo(2);
    assertThat(value(registry, "outbox")).isEqualTo(5);
    assertThat(registry.find(SettlementBacklogMetrics.BACKLOG).gauges()).hasSize(4);
  }

  private static double value(SimpleMeterRegistry registry, String kind) {
    return registry.get(SettlementBacklogMetrics.BACKLOG).tag("kind", kind).gauge().value();
  }
}
