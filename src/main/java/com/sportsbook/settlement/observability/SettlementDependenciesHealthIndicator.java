package com.sportsbook.settlement.observability;

import java.util.Map;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public final class SettlementDependenciesHealthIndicator implements HealthIndicator {

  private static final String WORK_QUERY =
      """
      select
        (select count(*) from settlement_revision
          where state = 'BLOCKED' and next_retry_at is null
            and last_error_code is not null) paused,
        (select count(*) from settlement_revision where state = 'EXHAUSTED') exhausted,
        (select count(*) from outbox_event where published_at is null) outbox
      """;

  private final JdbcTemplate jdbc;

  public SettlementDependenciesHealthIndicator(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Health health() {
    try {
      Map<String, Object> work = jdbc.queryForMap(WORK_QUERY);
      return Health.up()
          .withDetail("database", "reachable")
          .withDetail("pausedRevisions", count(work, "paused"))
          .withDetail("exhaustedRevisions", count(work, "exhausted"))
          .withDetail("outboxBacklog", count(work, "outbox"))
          .build();
    } catch (DataAccessException ignored) {
      return Health.down().withDetail("database", "unreachable").build();
    }
  }

  private static long count(Map<String, Object> work, String key) {
    Object value = work.get(key);
    return value instanceof Number number ? number.longValue() : 0;
  }
}
