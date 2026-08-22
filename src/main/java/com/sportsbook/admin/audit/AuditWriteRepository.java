package com.sportsbook.admin.audit;

import com.sportsbook.admin.context.AdminContext;
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
}
