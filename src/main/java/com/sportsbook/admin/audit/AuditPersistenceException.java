package com.sportsbook.admin.audit;

import java.util.UUID;

public final class AuditPersistenceException extends RuntimeException {

  public enum Phase {
    BEGIN,
    COMPLETE
  }

  private final UUID actionId;
  private final Phase phase;

  AuditPersistenceException(UUID actionId, Phase phase, Throwable cause) {
    super("Audit persistence failed during " + phase, cause);
    this.actionId = actionId;
    this.phase = phase;
  }

  public UUID actionId() {
    return actionId;
  }

  public Phase phase() {
    return phase;
  }
}
