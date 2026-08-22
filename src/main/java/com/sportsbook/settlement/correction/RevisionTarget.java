package com.sportsbook.settlement.correction;

import com.sportsbook.protocol.domain.BetSlipType;
import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.settlement.resolver.ResolvedSelection;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Immutable bet and accepted-result snapshot used to calculate one correction. */
public record RevisionTarget(
    UUID betId,
    long revisionNumber,
    UUID userId,
    UUID eventId,
    UUID sourceCandidateId,
    SettlementResult previousResult,
    Money previousPayout,
    BetSlipType slipType,
    Money unitStake,
    List<ResolvedSelection> selections,
    Instant sourceResultSettledAt) {

  public RevisionTarget {
    Objects.requireNonNull(betId, "betId");
    Objects.requireNonNull(userId, "userId");
    Objects.requireNonNull(eventId, "eventId");
    Objects.requireNonNull(sourceCandidateId, "sourceCandidateId");
    Objects.requireNonNull(previousResult, "previousResult");
    Objects.requireNonNull(previousPayout, "previousPayout");
    Objects.requireNonNull(slipType, "slipType");
    Objects.requireNonNull(unitStake, "unitStake");
    selections = List.copyOf(Objects.requireNonNull(selections, "selections"));
    Objects.requireNonNull(sourceResultSettledAt, "sourceResultSettledAt");
    if (revisionNumber < 1 || selections.isEmpty()) {
      throw new IllegalArgumentException("Revision target must have a sequence and selections");
    }
    if (previousPayout.currency() != unitStake.currency()) {
      throw new IllegalArgumentException("Revision target money currencies must match");
    }
  }
}
