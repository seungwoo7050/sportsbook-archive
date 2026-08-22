package com.sportsbook.admin.audit;

import com.sportsbook.admin.event.AdminActionRecorded;

public final class AuditEventMapper {

  private AuditEventMapper() {}

  public static AdminActionRecorded toEvent(AuditTerminalRecord record) {
    return AdminActionRecorded.newBuilder()
        .setActionId(record.actionId().toString())
        .setActorId(record.actorId())
        .setActorRole(record.actorRole().name())
        .setAction(record.action())
        .setTarget(record.target())
        .setOutcome(record.outcome().name())
        .setHttpStatus(record.httpStatus())
        .setReason(record.reason())
        .setTraceId(record.traceId())
        .setStartedAt(record.startedAt())
        .setCompletedAt(record.completedAt())
        .build();
  }
}
