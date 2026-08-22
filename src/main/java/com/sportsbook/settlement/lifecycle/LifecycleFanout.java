package com.sportsbook.settlement.lifecycle;

import com.sportsbook.protocol.event.EventLifecycleStatus;
import com.sportsbook.settlement.execution.SettlementExecution;
import com.sportsbook.settlement.execution.SettlementExecutionRunner;
import com.sportsbook.settlement.persistence.BetRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class LifecycleFanout {

  private final BetRepository bets;
  private final LifecycleSettlementPreparer preparer;
  private final SettlementExecutionRunner runner;

  public LifecycleFanout(
      BetRepository bets, LifecycleSettlementPreparer preparer, SettlementExecutionRunner runner) {
    this.bets = bets;
    this.preparer = preparer;
    this.runner = runner;
  }

  public SettlementExecutionRunner.BatchResult fanOut(LifecycleObservation tombstone) {
    String reason = reason(tombstone.status());
    List<SettlementExecution> executions = new ArrayList<>();
    for (var betId : bets.findPendingIdsByEvent(tombstone.eventId())) {
      preparer.prepare(betId, tombstone.eventId(), reason).ifPresent(executions::add);
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
}
