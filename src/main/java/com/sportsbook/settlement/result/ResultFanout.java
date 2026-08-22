package com.sportsbook.settlement.result;

import com.sportsbook.settlement.execution.SettlementExecution;
import com.sportsbook.settlement.execution.SettlementExecutionRunner;
import com.sportsbook.settlement.observability.SettlementMetrics;
import com.sportsbook.settlement.persistence.BetRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ResultFanout {

  private final BetRepository bets;
  private final ResultSettlementPreparer preparer;
  private final SettlementExecutionRunner runner;
  private final SettlementMetrics metrics;

  public ResultFanout(
      BetRepository bets,
      ResultSettlementPreparer preparer,
      SettlementExecutionRunner runner,
      SettlementMetrics metrics) {
    this.bets = bets;
    this.preparer = preparer;
    this.runner = runner;
    this.metrics = metrics;
  }

  public SettlementExecutionRunner.BatchResult fanOut(AcceptedResult accepted) {
    var sample = metrics.start();
    try {
      List<SettlementExecution> executions = new ArrayList<>();
      for (var betId : bets.findResultActionableIdsByEvent(accepted.eventId())) {
        preparer.prepare(betId, accepted).ifPresent(executions::add);
      }
      var result = runner.fanOut(List.copyOf(executions));
      metrics.count("base_result", "succeeded", result.succeeded());
      metrics.count("base_result", "failed", result.failed());
      return result;
    } catch (RuntimeException failure) {
      metrics.count("base_result", "failed");
      throw failure;
    } finally {
      metrics.stop(sample, "base_result");
    }
  }
}
