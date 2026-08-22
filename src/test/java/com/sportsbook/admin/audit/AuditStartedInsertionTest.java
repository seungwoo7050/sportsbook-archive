package com.sportsbook.admin.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.admin.context.AdminContext;
import com.sportsbook.admin.security.AdminRole;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@JdbcTest(properties = "spring.flyway.enabled=true")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(AuditWriteRepository.class)
@Testcontainers
class AuditStartedInsertionTest {

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
  void insertsStartedAndPreventsDownstreamWorkWhenThatGateFails() {
    UUID actionId = UUID.fromString("018f0000-0000-7000-8000-000000000021");
    AdminContext context = new AdminContext("operator-1", AdminRole.ADMIN, actionId, "trace-1");
    auditWrites.begin(context, "WALLET_REFUND", "user-1", "refund");

    assertThat(
            jdbc.queryForObject(
                "SELECT outcome FROM audit_log WHERE action_id = ?", String.class, actionId))
        .isEqualTo("STARTED");
    assertThat(
            jdbc.queryForObject(
                "SELECT http_status IS NULL AND completed_at IS NULL "
                    + "FROM audit_log WHERE action_id = ?",
                Boolean.class,
                actionId))
        .isTrue();

    AtomicInteger downstreamCalls = new AtomicInteger();
    assertThatThrownBy(
            () -> {
              auditWrites.begin(context, "WALLET_REFUND", "user-1", "refund");
              downstreamCalls.incrementAndGet();
            })
        .isInstanceOf(DataAccessException.class);
    assertThat(downstreamCalls).hasValue(0);
  }
}
