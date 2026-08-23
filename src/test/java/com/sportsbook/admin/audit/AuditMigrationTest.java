package com.sportsbook.admin.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class AuditMigrationTest {

  @Container
  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine");

  @BeforeEach
  void cleanDatabase() {
    flyway().clean();
  }

  @Test
  void migratesACleanDatabaseThroughV1AndV2() throws Exception {
    assertThat(flyway().migrate().migrationsExecuted).isEqualTo(2);

    try (Connection connection = connection(); Statement statement = connection.createStatement()) {
      assertThat(
              scalar(
                  statement,
                  "SELECT count(*) FROM information_schema.columns "
                      + "WHERE table_name='audit_log' "
                      + "AND column_name IN ('started_at','completed_at')"))
          .isEqualTo(2);
      assertThat(
              scalar(
                  statement,
                  "SELECT count(*) FROM pg_constraint "
                      + "WHERE conname LIKE 'chk_audit_log_%'"))
          .isEqualTo(3);
      assertThat(
              scalar(
                  statement,
                  "SELECT count(*) FROM pg_indexes "
                      + "WHERE indexname='idx_audit_log_stale_started'"))
          .isEqualTo(1);
    }
  }

  private static Flyway flyway() {
    return Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .locations("classpath:db/migration")
        .cleanDisabled(false)
        .load();
  }

  private static Connection connection() throws Exception {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
  }

  private static int scalar(Statement statement, String sql) throws Exception {
    try (ResultSet result = statement.executeQuery(sql)) {
      assertThat(result.next()).isTrue();
      return result.getInt(1);
    }
  }
}
