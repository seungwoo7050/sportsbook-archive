package com.sportsbook.settlement.correction;

import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.settlement.domain.Bet;
import com.sportsbook.settlement.domain.SettlementStatus;
import com.sportsbook.settlement.resolver.ResolvedSelection;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ReplacementSnapshotProjector {

  public Optional<RevisionTarget> project(Bet bet, ResultCandidate source) {
    if (bet.status() != SettlementStatus.SETTLED
        || source.state() != ResultCandidateState.ACCEPTED) {
      throw new IllegalArgumentException("Correction requires settled bet and accepted result");
    }
    List<ResolvedSelection> resolved = new ArrayList<>(bet.selections().size());
    boolean sourceEventPresent = false;
    for (var selection : bet.selections()) {
      SettlementResult outcome = selection.outcome();
      if (selection.eventId().equals(source.eventId())) {
        sourceEventPresent = true;
        Optional<SettlementResult> replacement =
            source.mode().resolve(source.outcomes().get(selection.selectionId()));
        if (replacement.isEmpty()) {
          return Optional.empty();
        }
        outcome = replacement.orElseThrow();
      }
      resolved.add(new ResolvedSelection(selection.selectionId(), selection.odds(), outcome));
    }
    if (!sourceEventPresent) {
      throw new IllegalArgumentException("Accepted result is unrelated to the bet");
    }
    return Optional.of(
        new RevisionTarget(
            bet.betId(),
            Math.incrementExact(bet.revisionNumber()),
            bet.userId(),
            source.eventId(),
            source.candidateId(),
            bet.result(),
            bet.payout(),
            bet.slipType(),
            bet.stake(),
            resolved,
            source.settledAt()));
  }
}
