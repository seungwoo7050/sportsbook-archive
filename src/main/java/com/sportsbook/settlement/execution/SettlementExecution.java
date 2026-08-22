package com.sportsbook.settlement.execution;

import java.util.Objects;
import java.util.UUID;

public record SettlementExecution(SettlementAttempt attempt, UUID userId) {

  public SettlementExecution {
    Objects.requireNonNull(attempt, "attempt");
    Objects.requireNonNull(userId, "userId");
  }
}
