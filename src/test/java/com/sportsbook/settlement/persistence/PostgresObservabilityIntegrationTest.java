package com.sportsbook.settlement.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.settlement.observability.SettlementBacklogMetrics;
import com.sportsbook.settlement.observability.SettlementDependenciesHealthIndicator;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Status;

class PostgresObservabilityIntegrationTest extends PostgresIntegrationSupport {

  @Autowired private SettlementDependenciesHealthIndicator health;
  @Autowired private PrometheusMeterRegistry metrics;

  @Test
  void executesHealthAndBacklogQueriesAgainstPostgres() {
    insertPendingBet(UUID.randomUUID());

    var snapshot = health.health();

    assertThat(snapshot.getStatus()).isEqualTo(Status.UP);
    assertThat(snapshot.getDetails())
        .containsEntry("pausedRevisions", 0L)
        .containsEntry("exhaustedRevisions", 0L)
        .containsEntry("outboxBacklog", 0L);
    assertThat(backlog("pending_bets")).isEqualTo(1);
    assertThat(backlog("blocked_revisions")).isZero();
    assertThat(backlog("exhausted_revisions")).isZero();
    assertThat(backlog("outbox")).isZero();
  }

  private double backlog(String kind) {
    return metrics.get(SettlementBacklogMetrics.BACKLOG).tag("kind", kind).gauge().value();
  }
}
