package com.sportsbook.admin.api;

import com.sportsbook.admin.audit.AuditLogEntity;
import java.time.Instant;
import java.util.UUID;

public record AuditLogView(
    UUID actionId,
    String actorId,
    String actorRole,
    String action,
    String target,
    String outcome,
    Integer httpStatus,
    String reason,
    String traceId,
    Instant startedAt,
    Instant completedAt) {

  public static AuditLogView from(AuditLogEntity entity) {
    return new AuditLogView(
        entity.getActionId(),
        entity.getActorId(),
        entity.getActorRole().name(),
        entity.getAction(),
        entity.getTarget(),
        entity.getOutcome().name(),
        entity.getHttpStatus(),
        entity.getReason(),
        entity.getTraceId(),
        entity.getStartedAt(),
        entity.getCompletedAt());
  }
}
