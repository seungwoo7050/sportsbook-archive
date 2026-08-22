package com.sportsbook.settlement.correction;

import com.sportsbook.settlement.config.SettlementRuntimeProperties;
import com.sportsbook.settlement.result.AcceptedResult;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CorrectionFanout {

  private final CorrectionTargetRepository targets;
  private final CorrectionRevisionPreparer preparer;
  private final RevisionExecutionRunner runner;
  private final SettlementRuntimeProperties runtime;

  public CorrectionFanout(
      CorrectionTargetRepository targets,
      CorrectionRevisionPreparer preparer,
      RevisionExecutionRunner runner,
      SettlementRuntimeProperties runtime) {
    this.targets = targets;
    this.preparer = preparer;
    this.runner = runner;
    this.runtime = runtime;
  }

  public List<RevisionExecutionRunner.Result> fanOut(AcceptedResult accepted) {
    var ids =
        targets.findActionable(accepted.eventId(), accepted.candidateId(), runtime.batchSize());
    List<RevisionExecutionRunner.Result> results = new ArrayList<>(ids.size());
    for (var betId : ids) {
      preparer
          .prepare(betId, accepted)
          .ifPresent(
              prepared -> results.add(runner.execute(prepared.plan(), prepared.lease(), false)));
    }
    return List.copyOf(results);
  }
}
