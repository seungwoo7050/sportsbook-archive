package com.sportsbook.settlement.result;

import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.settlement.config.SettlementRuntimeProperties;
import com.sportsbook.settlement.domain.Bet;
import com.sportsbook.settlement.domain.SettlementStatus;
import com.sportsbook.settlement.execution.SettlementAttemptRepository;
import com.sportsbook.settlement.execution.SettlementExecution;
import com.sportsbook.settlement.persistence.BetRepository;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResultSettlementPreparer {

  private final BetRepository bets;
  private final SettlementAttemptRepository attempts;
  private final BaseSettlementPlanner planner;
  private final SettlementRuntimeProperties runtime;
  private final Clock clock;

  public ResultSettlementPreparer(
      BetRepository bets,
      SettlementAttemptRepository attempts,
      BaseSettlementPlanner planner,
      SettlementRuntimeProperties runtime,
      Clock clock) {
    this.bets = bets;
    this.attempts = attempts;
    this.planner = planner;
    this.runtime = runtime;
    this.clock = clock;
  }

  @Transactional
  public Optional<SettlementExecution> prepare(UUID betId, AcceptedResult accepted) {
    Bet bet = bets.findForUpdateById(betId).orElseThrow();
    if (bet.status() != SettlementStatus.PENDING || attempts.exists(betId)) {
      return Optional.empty();
    }
    var outcomes = new LinkedHashMap<UUID, SettlementResult>();
    boolean related = false;
    for (var selection : bet.selections()) {
      if (selection.eventId().equals(accepted.eventId())) {
        related = true;
        accepted
            .resolve(selection.selectionId())
            .ifPresent(value -> outcomes.put(selection.selectionId(), value));
      }
    }
    if (!related) {
      throw new IllegalArgumentException("Accepted result is unrelated to pending bet");
    }
    bet.applyAcceptedResult(accepted.eventId(), accepted.candidateId(), outcomes, clock.instant());
    if (!bet.allSelectionsResolved()) {
      return Optional.empty();
    }
    var draft = planner.plan(bet, accepted.eventId());
    return attempts
        .claimPending(draft, runtime.leaseDuration())
        .map(claimed -> new SettlementExecution(claimed, bet.userId()))
        .or(
            () -> {
              throw new IllegalStateException("Initial result claim lost after bet lock");
            });
  }
}
