package com.sportsbook.admin.audit;

public enum AuditOutcome {
  STARTED,
  SUCCESS,
  FAILED,
  UNKNOWN;

  public boolean isTerminal() {
    return this != STARTED;
  }
}
