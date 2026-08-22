package com.sportsbook.admin.audit;

import com.sportsbook.admin.context.AdminContext;
import com.sportsbook.admin.security.AdminRole;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class AuditWriteRepository {

  private static final String INSERT_STARTED =
      """
      INSERT INTO audit_log
          (action_id, actor_id, actor_role, action, target, outcome,
           http_status, reason, trace_id, started_at, completed_at)
      VALUES (?, ?, ?, ?, ?, 'STARTED', NULL, ?, ?, CURRENT_TIMESTAMP, NULL)
      """;

  private static final String COMPLETE_STARTED =
      """
      UPDATE audit_log
      SET outcome = ?, http_status = ?, completed_at = CURRENT_TIMESTAMP
      WHERE action_id = ? AND outcome = 'STARTED'
      RETURNING action_id, actor_id, actor_role, action, target, outcome,
                http_status, reason, trace_id, started_at, completed_at
      """;

  private final JdbcTemplate jdbc;

  public AuditWriteRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
  public void begin(AdminContext context, String action, String target, String reason) {
    int inserted =
        jdbc.update(
            INSERT_STARTED,
            context.actionId(),
            context.actorId(),
            context.actorRole().name(),
            action,
            target,
            reason,
            context.traceId());
    if (inserted != 1) {
      throw new IllegalStateException("Audit STARTED insertion did not affect exactly one row");
    }
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
  public AuditTerminalRecord complete(
      UUID actionId, AuditOutcome outcome, Integer httpStatus) {
    if (!outcome.isTerminal()) {
      throw new IllegalArgumentException("Cannot complete an audit row as STARTED");
    }
    List<AuditTerminalRecord> updated =
        jdbc.query(
            COMPLETE_STARTED,
            (result, rowNumber) ->
                new AuditTerminalRecord(
                    result.getObject("action_id", UUID.class),
                    result.getString("actor_id"),
                    AdminRole.valueOf(result.getString("actor_role")),
                    result.getString("action"),
                    result.getString("target"),
                    AuditOutcome.valueOf(result.getString("outcome")),
                    result.getObject("http_status", Integer.class),
                    result.getString("reason"),
                    result.getString("trace_id"),
                    timestamp(result.getTimestamp("started_at")),
                    timestamp(result.getTimestamp("completed_at"))),
            outcome.name(),
            httpStatus,
            actionId);
    if (updated.size() != 1) {
      throw new IllegalStateException("Audit terminal update did not claim exactly one STARTED row");
    }
    return updated.get(0);
  }

  private static java.time.Instant timestamp(Timestamp timestamp) {
    return timestamp.toInstant();
  }
}
