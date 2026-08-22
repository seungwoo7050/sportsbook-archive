package com.sportsbook.settlement.observability;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public final class SettlementBacklogMetrics implements MeterBinder {

  public static final String BACKLOG = "settlement.backlog";

  private final JdbcTemplate jdbc;

  public SettlementBacklogMetrics(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void bindTo(MeterRegistry registry) {
    bind(registry, "pending_bets", "select count(*) from bet where status = 'PENDING'");
    bind(
        registry,
        "blocked_revisions",
        "select count(*) from settlement_revision where state = 'BLOCKED'");
    bind(
        registry,
        "exhausted_revisions",
        "select count(*) from settlement_revision where state = 'EXHAUSTED'");
    bind(registry, "outbox", "select count(*) from outbox_event where published_at is null");
  }

  private void bind(MeterRegistry registry, String kind, String sql) {
    Gauge.builder(BACKLOG, jdbc, template -> count(template, sql))
        .tag("kind", kind)
        .description("Durable settlement work awaiting completion")
        .register(registry);
  }

  private static double count(JdbcTemplate jdbc, String sql) {
    return Objects.requireNonNull(jdbc.queryForObject(sql, Long.class));
  }
}
