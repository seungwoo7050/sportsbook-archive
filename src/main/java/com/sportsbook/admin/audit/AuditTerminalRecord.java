package com.sportsbook.admin.audit;

import com.sportsbook.admin.security.AdminRole;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record AuditTerminalRecord(
    UUID actionId,
    String actorId,
    AdminRole actorRole,
    String action,
    String target,
    AuditOutcome outcome,
    Integer httpStatus,
    String reason,
    String traceId,
    Instant startedAt,
    Instant completedAt) {

  public AuditTerminalRecord {
    Objects.requireNonNull(actionId, "actionId");
    Objects.requireNonNull(actorId, "actorId");
    Objects.requireNonNull(actorRole, "actorRole");
    Objects.requireNonNull(action, "action");
    Objects.requireNonNull(outcome, "outcome");
    Objects.requireNonNull(startedAt, "startedAt");
    Objects.requireNonNull(completedAt, "completedAt");
    if (!outcome.isTerminal()) {
      throw new IllegalArgumentException("Terminal audit record cannot be STARTED");
    }
    if (outcome != AuditOutcome.UNKNOWN && httpStatus == null) {
      throw new IllegalArgumentException("SUCCESS and FAILED require an HTTP status");
    }
  }
}
