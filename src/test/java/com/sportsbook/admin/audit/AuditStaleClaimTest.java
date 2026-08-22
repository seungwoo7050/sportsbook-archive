package com.sportsbook.admin.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.admin.context.AdminContext;
import com.sportsbook.admin.security.AdminRole;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@JdbcTest(properties = "spring.flyway.enabled=true")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(AuditWriteRepository.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
class AuditStaleClaimTest {

  @Container
  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine");

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired private AuditWriteRepository auditWrites;
  @Autowired private JdbcTemplate jdbc;

  @Test
  void transitionsOnlyOldStartedRowsToUnknown() {
    UUID stale = UUID.fromString("018f0000-0000-7000-8000-000000000081");
    UUID fresh = UUID.fromString("018f0000-0000-7000-8000-000000000082");
    UUID terminal = UUID.fromString("018f0000-0000-7000-8000-000000000083");
    begin(stale);
    begin(fresh);
    begin(terminal);
    jdbc.update(
        "UPDATE audit_log SET started_at=CURRENT_TIMESTAMP-INTERVAL '10 minutes' "
            + "WHERE action_id IN (?, ?)",
        stale,
        terminal);
    auditWrites.complete(terminal, AuditOutcome.SUCCESS, 202);

    List<AuditTerminalRecord> claimed = auditWrites.claimStale(Duration.ofMinutes(5), 100);

    assertThat(claimed)
        .singleElement()
        .satisfies(
            record -> {
              assertThat(record.actionId()).isEqualTo(stale);
              assertThat(record.outcome()).isEqualTo(AuditOutcome.UNKNOWN);
              assertThat(record.httpStatus()).isNull();
              assertThat(record.completedAt()).isAfterOrEqualTo(record.startedAt());
            });
    assertThat(outcome(fresh)).isEqualTo("STARTED");
    assertThat(outcome(terminal)).isEqualTo("SUCCESS");
  }

  private void begin(UUID actionId) {
    auditWrites.begin(
        new AdminContext("operator-1", AdminRole.ADMIN, actionId, "trace-1"),
        "MARKET_CLOSE",
        "market-1",
        "close");
  }

  private String outcome(UUID actionId) {
    return jdbc.queryForObject(
        "SELECT outcome FROM audit_log WHERE action_id = ?", String.class, actionId);
  }
}
