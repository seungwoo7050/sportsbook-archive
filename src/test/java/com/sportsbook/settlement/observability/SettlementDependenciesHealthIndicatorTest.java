package com.sportsbook.settlement.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.actuate.health.Status;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

class SettlementDependenciesHealthIndicatorTest {

  @Test
  void reportsStalledWorkWithoutFailingReadiness() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.queryForMap(anyString()))
        .thenReturn(Map.of("paused", 2L, "exhausted", 3L, "outbox", 5L));

    var health = new SettlementDependenciesHealthIndicator(jdbc).health();

    assertThat(health.getStatus()).isEqualTo(Status.UP);
    assertThat(health.getDetails())
        .containsEntry("database", "reachable")
        .containsEntry("pausedRevisions", 2L)
        .containsEntry("exhaustedRevisions", 3L)
        .containsEntry("outboxBacklog", 5L);
    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    verify(jdbc, atLeastOnce()).queryForMap(sql.capture());
    assertThat(sql.getValue())
        .contains("state = 'BLOCKED'", "next_retry_at is null", "last_error_code is not null")
        .doesNotContain("attempt_count = 12");
  }

  @Test
  void hidesDatabaseFailureDetails() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.queryForMap(anyString()))
        .thenThrow(new DataAccessResourceFailureException("jdbc:postgresql://secret-host/db"));

    var health = new SettlementDependenciesHealthIndicator(jdbc).health();

    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    assertThat(health.getDetails()).containsOnly(Map.entry("database", "unreachable"));
    assertThat(health.toString()).doesNotContain("secret-host");
  }
}
