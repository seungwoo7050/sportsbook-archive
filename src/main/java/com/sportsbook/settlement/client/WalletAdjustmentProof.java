package com.sportsbook.settlement.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.Money;
import java.time.Instant;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WalletAdjustmentProof(
    UUID revisionId,
    UUID betId,
    long revisionNumber,
    UUID userId,
    Money previousPayout,
    Money newPayout,
    long deltaAmount,
    Currency currency,
    Status status,
    Long queueSequence,
    UUID operationGroupId,
    Instant queuedAt,
    Instant appliedAt,
    Instant nextAttemptAt) {

  public enum Status {
    APPLIED,
    BLOCKED,
    REJECTED
  }
}
