package com.sportsbook.settlement.lifecycle;

import com.sportsbook.protocol.domain.BetSlipType;
import com.sportsbook.protocol.event.EventLifecycleStatus;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.settlement.config.SettlementRuntimeProperties;
import com.sportsbook.settlement.domain.Bet;
import com.sportsbook.settlement.execution.SettlementAttempt;
import com.sportsbook.settlement.execution.SettlementAttemptRepository;
import com.sportsbook.settlement.execution.SettlementExecution;
import com.sportsbook.settlement.execution.SettlementExecutionRunner;
import com.sportsbook.settlement.execution.SettlementLease;
import com.sportsbook.settlement.persistence.BetRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class LifecycleFanout {

  private final BetRepository bets;
  private final SettlementAttemptRepository attempts;
  private final SettlementExecutionRunner runner;
  private final SettlementRuntimeProperties runtime;
  private final Clock clock;

  public LifecycleFanout(
      BetRepository bets,
      SettlementAttemptRepository attempts,
      SettlementExecutionRunner runner,
      SettlementRuntimeProperties runtime,
      Clock clock) {
    this.bets = bets;
    this.attempts = attempts;
    this.runner = runner;
    this.runtime = runtime;
    this.clock = clock;
  }

  public SettlementExecutionRunner.BatchResult fanOut(LifecycleObservation tombstone) {
    String reason = reason(tombstone.status());
    Instant now = clock.instant();
    List<SettlementExecution> executions = new ArrayList<>();
    for (var betId : bets.findPendingIdsByEvent(tombstone.eventId())) {
      Bet bet = bets.findWithSelectionsById(betId).orElseThrow();
      SettlementAttempt attempt =
          SettlementAttempt.wholeSlipVoid(
              bet.betId(),
              tombstone.eventId(),
              reason,
              totalExposure(bet),
              SettlementLease.acquire(now, runtime.leaseDuration()),
              now);
      if (attempts.claimPending(attempt)) {
        executions.add(new SettlementExecution(attempt, bet.userId()));
      }
    }
    return runner.fanOut(executions);
  }

  private static String reason(EventLifecycleStatus status) {
    return switch (status) {
      case CANCELLED -> "EVENT_CANCELLED";
      case POSTPONED -> "EVENT_POSTPONED";
      default -> throw new IllegalArgumentException("Lifecycle fanout requires terminal status");
    };
  }

  private static Money totalExposure(Bet bet) {
    long lines = 1;
    if (bet.slipType() instanceof BetSlipType.System system) {
      lines = combinations(system.totalSelections(), system.minWins());
    }
    return bet.stake().multiply(lines);
  }

  private static long combinations(int n, int k) {
    long result = 1;
    for (int factor = 1; factor <= k; factor++) {
      result = Math.multiplyExact(result, n - k + factor) / factor;
    }
    return result;
  }
}
