package com.sportsbook.admin.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.admin.context.AdminContext;
import com.sportsbook.admin.security.AdminRole;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
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
class AuditTerminalRaceTest {

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
  void exactlyOneConcurrentTerminalUpdateCanClaimStarted() throws Exception {
    UUID actionId = UUID.fromString("018f0000-0000-7000-8000-000000000031");
    auditWrites.begin(
        new AdminContext("operator-1", AdminRole.ADMIN, actionId, "trace-1"),
        "MARKET_CLOSE",
        "market-1",
        "close");
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService workers = Executors.newFixedThreadPool(2);

    try {
      Future<Object> success =
          workers.submit(() -> completeAfter(start, actionId, AuditOutcome.SUCCESS, 202));
      Future<Object> failed =
          workers.submit(() -> completeAfter(start, actionId, AuditOutcome.FAILED, 409));
      start.countDown();
      List<Object> outcomes = List.of(result(success), result(failed));

      assertThat(outcomes).filteredOn(AuditTerminalRecord.class::isInstance).hasSize(1);
      assertThat(outcomes).filteredOn(IllegalStateException.class::isInstance).hasSize(1);
      assertThat(
              jdbc.queryForObject(
                  "SELECT outcome FROM audit_log WHERE action_id = ?", String.class, actionId))
          .isIn("SUCCESS", "FAILED");
    } finally {
      workers.shutdownNow();
    }
  }

  private Object completeAfter(
      CountDownLatch start, UUID actionId, AuditOutcome outcome, int status) throws Exception {
    start.await();
    return auditWrites.complete(actionId, outcome, status);
  }

  private static Object result(Future<Object> future) throws Exception {
    try {
      return future.get();
    } catch (ExecutionException failure) {
      return failure.getCause();
    }
  }
}
