package com.sportsbook.settlement.correction;

import com.sportsbook.settlement.client.WalletAdjustmentProof;
import com.sportsbook.settlement.config.SettlementTopics;
import com.sportsbook.settlement.domain.Bet;
import com.sportsbook.settlement.domain.SettlementStatus;
import com.sportsbook.settlement.outbox.OutboxEventRepository;
import com.sportsbook.settlement.outbox.SettlementEventFactory;
import com.sportsbook.settlement.outbox.StrictAvroEncoder;
import com.sportsbook.settlement.persistence.BetRepository;
import com.sportsbook.settlement.resolver.ResolvedSelection;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RevisionFinalizer {

  private final BetRepository bets;
  private final RevisionPlanRepository revisions;
  private final OutboxEventRepository outbox;
  private final SettlementEventFactory events;

  public RevisionFinalizer(
      BetRepository bets,
      RevisionPlanRepository revisions,
      OutboxEventRepository outbox,
      SettlementTopics topics) {
    this.bets = bets;
    this.revisions = revisions;
    this.outbox = outbox;
    this.events = new SettlementEventFactory(topics, new StrictAvroEncoder());
  }

  @Transactional
  public boolean apply(RevisionPlan plan, RevisionLease lease, WalletAdjustmentProof proof) {
    if (plan.requiresWalletAdjustment()) {
      new RevisionProofValidator().requireExact(plan, proof);
      if (proof.status() != WalletAdjustmentProof.Status.APPLIED) {
        throw new IllegalArgumentException("Only applied adjustments can finalize a revision");
      }
    } else if (proof != null) {
      throw new IllegalArgumentException("Zero-delta revisions must not contact Wallet");
    }
    RevisionTarget target = plan.target();
    Bet bet = bets.findForUpdateById(target.betId()).orElseThrow();
    Map<java.util.UUID, ResolvedSelection> snapshot =
        target.selections().stream()
            .collect(Collectors.toMap(ResolvedSelection::selectionId, Function.identity()));
    boolean stale =
        bet.status() != SettlementStatus.SETTLED
            || bet.revisionNumber() != target.revisionNumber() - 1
            || !bet.userId().equals(target.userId())
            || bet.result() != target.previousResult()
            || !target.previousPayout().equals(bet.payout())
            || snapshot.size() != bet.selections().size()
            || bet.selections().stream()
                .anyMatch(selection -> !snapshot.containsKey(selection.selectionId()))
            || bet.selections().stream()
                .noneMatch(selection -> selection.eventId().equals(target.eventId()));
    Optional<Instant> appliedAt =
        stale ? Optional.empty() : revisions.markApplied(plan.revisionId(), lease, proof);
    if (appliedAt.isEmpty()) {
      return false;
    }
    Instant revisedAt = appliedAt.orElseThrow();
    bet.selections()
        .forEach(
            selection -> {
              var resolved = snapshot.get(selection.selectionId());
              if (selection.eventId().equals(target.eventId())) {
                selection.applyCandidate(target.sourceCandidateId(), resolved.outcome());
              } else if (selection.outcome() != resolved.outcome()) {
                throw new IllegalStateException("Unrelated selection changed during correction");
              }
            });
    if (bet.recordRevision(plan.newResult(), plan.newPayout(), revisedAt)
        != target.revisionNumber()) {
      throw new IllegalStateException("Bet revision sequence diverged");
    }
    outbox.save(events.revised(plan, revisedAt));
    return true;
  }

  public boolean apply(
      RevisionPlan plan, RevisionLease lease, WalletAdjustmentProof proof, Instant ignored) {
    return apply(plan, lease, proof);
  }
}
