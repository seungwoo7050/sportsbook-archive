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

    try (Connection connection = connection();
        Statement statement = connection.createStatement()) {
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
                  "SELECT count(*) FROM pg_constraint " + "WHERE conname LIKE 'chk_audit_log_%'"))
          .isEqualTo(3);
      assertThat(
              scalar(
                  statement,
                  "SELECT count(*) FROM pg_indexes "
                      + "WHERE indexname='idx_audit_log_stale_started'"))
          .isEqualTo(1);
    }
  }

  @Test
  void upgradesLegacyV1RowsWithoutLosingTheirCompletionEvidence() throws Exception {
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .locations("classpath:db/migration")
        .target("1")
        .load()
        .migrate();
    try (Connection connection = connection();
        Statement statement = connection.createStatement()) {
      statement.executeUpdate(
          "INSERT INTO audit_log "
              + "(action_id,actor_id,actor_role,action,target,outcome,http_status,reason,trace_id,occurred_at) "
              + "VALUES ('018f0000-0000-7000-8000-000000000001','operator-1','ADMIN',"
              + "'WALLET_REFUND','user-1','SUCCESS',200,'approved','trace-1',"
              + "TIMESTAMPTZ '2026-08-23 00:00:00+00')");
    }

    assertThat(flyway().migrate().migrationsExecuted).isEqualTo(1);

    try (Connection connection = connection();
        Statement statement = connection.createStatement();
        ResultSet row =
            statement.executeQuery(
                "SELECT outcome,http_status,started_at=completed_at AS backfilled "
                    + "FROM audit_log WHERE actor_id='operator-1'")) {
      assertThat(row.next()).isTrue();
      assertThat(row.getString("outcome")).isEqualTo("SUCCESS");
      assertThat(row.getInt("http_status")).isEqualTo(200);
      assertThat(row.getBoolean("backfilled")).isTrue();
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
