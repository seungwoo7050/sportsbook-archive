package com.sportsbook.settlement.correction;

import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.settlement.infrastructure.id.UuidV7;
import com.sportsbook.settlement.resolver.SettlementOutcome;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Complete immutable correction plan allocated before any Wallet request. */
public record RevisionPlan(
    UUID revisionId,
    RevisionTarget target,
    SettlementResult newResult,
    Money newPayout,
    Instant createdAt) {

  public RevisionPlan {
    Objects.requireNonNull(revisionId, "revisionId");
    Objects.requireNonNull(target, "target");
    Objects.requireNonNull(newResult, "newResult");
    Objects.requireNonNull(newPayout, "newPayout");
    Objects.requireNonNull(createdAt, "createdAt");
    if (newPayout.isNegative() || newPayout.currency() != target.previousPayout().currency()) {
      throw new IllegalArgumentException("Revision payout must preserve currency and be nonnegative");
    }
  }

  public static RevisionPlan allocate(
      RevisionTarget target, SettlementOutcome resolution, Instant now) {
    Objects.requireNonNull(resolution, "resolution");
    return new RevisionPlan(
        UuidV7.generate(), target, resolution.result(), resolution.payout(), now);
  }

  public long deltaAmount() {
    return Math.subtractExact(newPayout.amount(), target.previousPayout().amount());
  }

  public boolean hasZeroDelta() {
    return deltaAmount() == 0;
  }
}
