package com.sportsbook.settlement.result;

import com.sportsbook.settlement.execution.SettlementExecution;
import com.sportsbook.settlement.execution.SettlementExecutionRunner;
import com.sportsbook.settlement.persistence.BetRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ResultFanout {

  private final BetRepository bets;
  private final ResultSettlementPreparer preparer;
  private final SettlementExecutionRunner runner;

  public ResultFanout(
      BetRepository bets, ResultSettlementPreparer preparer, SettlementExecutionRunner runner) {
    this.bets = bets;
    this.preparer = preparer;
    this.runner = runner;
  }

  public SettlementExecutionRunner.BatchResult fanOut(AcceptedResult accepted) {
    List<SettlementExecution> executions = new ArrayList<>();
    for (var betId : bets.findResultActionableIdsByEvent(accepted.eventId())) {
      preparer.prepare(betId, accepted).ifPresent(executions::add);
    }
    return runner.fanOut(List.copyOf(executions));
  }
}
