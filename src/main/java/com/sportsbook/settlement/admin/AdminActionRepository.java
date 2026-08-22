package com.sportsbook.settlement.admin;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class AdminActionRepository {

  private final JdbcTemplate jdbc;

  public AdminActionRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public Optional<AdminAction> lockAndFind(UUID idempotencyKey) {
    jdbc.query(
        "select pg_advisory_xact_lock(hashtextextended(cast(? as text), 0))",
        (result, rowNumber) -> Boolean.TRUE,
        idempotencyKey);
    return jdbc
        .query(
            "select * from settlement_admin_action where idempotency_key = ?",
            (result, rowNumber) -> read(result),
            idempotencyKey)
        .stream()
        .findFirst();
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public AdminAction append(
      UUID idempotencyKey,
      AdminAction.Kind kind,
      UUID targetId,
      String fingerprint,
      AdminAction.Outcome outcome,
      UUID executionToken) {
    return jdbc
        .query(
            """
            insert into settlement_admin_action (
                idempotency_key, action_kind, target_id, request_fingerprint,
                outcome, execution_token, created_at, completed_at)
            values (?, ?, ?, ?, ?, ?, current_timestamp, current_timestamp)
            returning *
            """,
            (result, rowNumber) -> read(result),
            idempotencyKey,
            kind.name(),
            targetId,
            fingerprint,
            outcome.name(),
            executionToken)
        .stream()
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Admin action insert returned no row"));
  }

  private static AdminAction read(ResultSet result) throws SQLException {
    return new AdminAction(
        result.getObject("idempotency_key", UUID.class),
        AdminAction.Kind.valueOf(result.getString("action_kind")),
        result.getObject("target_id", UUID.class),
        result.getString("request_fingerprint"),
        AdminAction.Outcome.valueOf(result.getString("outcome")),
        result.getObject("execution_token", UUID.class),
        result.getTimestamp("created_at").toInstant(),
        result.getTimestamp("completed_at").toInstant());
  }
}
