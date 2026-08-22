package com.sportsbook.betting.domain;

public enum CompensationState {
  NONE,
  REQUIRED,
  IN_PROGRESS,
  COMPLETED;

  public boolean pending() {
    return this == REQUIRED || this == IN_PROGRESS;
  }
}
