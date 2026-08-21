package com.sportsbook.wallet.service.command;

import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.wallet.domain.SystemAccountIds;
import java.util.Objects;
import java.util.UUID;

/** Validated settlement revision whose nonzero payout delta must be applied or queued. */
public record AdjustmentCommand(
    UUID revisionId,
    UUID betId,
    long revisionNumber,
    UUID userId,
    Money previousPayout,
    Money newPayout,
    IdempotencyKey idempotencyKey) {

  public AdjustmentCommand {
    Objects.requireNonNull(revisionId, "revisionId");
    Objects.requireNonNull(betId, "betId");
    Objects.requireNonNull(userId, "userId");
    Objects.requireNonNull(previousPayout, "previousPayout");
    Objects.requireNonNull(newPayout, "newPayout");
    Objects.requireNonNull(idempotencyKey, "idempotencyKey");
    if (SystemAccountIds.isSystemAccount(userId)) {
      throw new IllegalArgumentException("System UUID cannot receive an adjustment");
    }
    if (revisionNumber < 1L) {
      throw new IllegalArgumentException("Revision number must be at least one");
    }
    if (previousPayout.amount() < 0L || newPayout.amount() < 0L) {
      throw new IllegalArgumentException("Payout snapshots cannot be negative");
    }
    if (previousPayout.currency() != newPayout.currency()) {
      throw new IllegalArgumentException("Payout snapshot currencies must match");
    }
    if (previousPayout.amount() == newPayout.amount()) {
      throw new IllegalArgumentException("Adjustment delta cannot be zero");
    }
    String expectedKey = "settlement:revision:" + revisionId;
    if (!idempotencyKey.value().equals(expectedKey)) {
      throw new IllegalArgumentException("Idempotency key must identify the revision");
    }
  }

  public long deltaAmount() {
    return Math.subtractExact(newPayout.amount(), previousPayout.amount());
  }

  public Money absoluteDelta() {
    return new Money(Math.abs(deltaAmount()), previousPayout.currency());
  }
}
