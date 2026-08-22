package com.sportsbook.settlement.correction;

import com.sportsbook.settlement.config.SettlementRuntimeProperties;
import com.sportsbook.settlement.domain.Bet;
import com.sportsbook.settlement.domain.SettlementStatus;
import com.sportsbook.settlement.persistence.BetRepository;
import com.sportsbook.settlement.persistence.DatabaseTimeSource;
import com.sportsbook.settlement.result.AcceptedResult;
import com.sportsbook.settlement.result.AcceptedResultRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CorrectionRevisionPreparer {

  private final BetRepository bets;
  private final AcceptedResultRepository acceptedResults;
  private final RevisionPlanRepository revisions;
  private final SettlementRuntimeProperties runtime;
  private final DatabaseTimeSource databaseTime;
  private final ReplacementSnapshotProjector projector = new ReplacementSnapshotProjector();
  private final RevisionResolver resolver = new RevisionResolver();

  public CorrectionRevisionPreparer(
      BetRepository bets,
      AcceptedResultRepository acceptedResults,
      RevisionPlanRepository revisions,
      SettlementRuntimeProperties runtime,
      DatabaseTimeSource databaseTime) {
    this.bets = bets;
    this.acceptedResults = acceptedResults;
    this.revisions = revisions;
    this.runtime = runtime;
    this.databaseTime = databaseTime;
  }

  @Transactional
  public Optional<PreparedRevision> prepare(UUID betId, AcceptedResult expected) {
    Bet bet = bets.findForUpdateById(betId).orElseThrow();
    if (bet.status() != SettlementStatus.SETTLED || !isStale(bet, expected)) {
      return Optional.empty();
    }
    AcceptedResult current =
        acceptedResults
            .findByEventId(expected.eventId())
            .filter(result -> result.candidateId().equals(expected.candidateId()))
            .orElse(null);
    long nextRevision = Math.incrementExact(bet.revisionNumber());
    if (current == null || revisions.exists(betId, nextRevision)) {
      return Optional.empty();
    }
    var target = projector.project(bet, current);
    if (target.isEmpty()) {
      return Optional.empty();
    }
    var resolution = resolver.resolve(target.orElseThrow());
    var requested =
        RevisionPlan.allocate(target.orElseThrow(), resolution, databaseTime.currentTimestamp());
    var persisted = revisions.persist(requested, runtime.leaseDuration());
    if (!persisted.created()) {
      return Optional.empty();
    }
    return Optional.of(new PreparedRevision(persisted.durablePlan(requested), persisted.lease()));
  }

  private static boolean isStale(Bet bet, AcceptedResult accepted) {
    return bet.selections().stream()
        .filter(selection -> selection.eventId().equals(accepted.eventId()))
        .anyMatch(selection -> !accepted.candidateId().equals(selection.sourceCandidateId()));
  }

  public record PreparedRevision(RevisionPlan plan, RevisionLease lease) {}
}
