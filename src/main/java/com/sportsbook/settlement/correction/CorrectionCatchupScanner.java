package com.sportsbook.settlement.correction;

import com.sportsbook.settlement.config.SettlementWorkerConfiguration;
import com.sportsbook.settlement.result.AcceptedResultRepository;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CorrectionCatchupScanner {

  private final CorrectionTargetRepository targets;
  private final AcceptedResultRepository acceptedResults;
  private final CorrectionFanout fanout;

  public CorrectionCatchupScanner(
      CorrectionTargetRepository targets,
      AcceptedResultRepository acceptedResults,
      CorrectionFanout fanout) {
    this.targets = targets;
    this.acceptedResults = acceptedResults;
    this.fanout = fanout;
  }

  @Scheduled(
      fixedDelayString = "${settlement.runtime.recovery-interval:PT1S}",
      initialDelayString = "${settlement.runtime.recovery-interval:PT1S}",
      scheduler = SettlementWorkerConfiguration.CORRECTION)
  public List<RevisionExecutionRunner.Result> catchUp() {
    var eventId = targets.findNextActionableEvent();
    if (eventId.isEmpty()) {
      return List.of();
    }
    var accepted =
        acceptedResults
            .findByEventId(eventId.orElseThrow())
            .orElseThrow(
                () -> new IllegalStateException("Actionable correction lost its accepted result"));
    return fanout.fanOut(accepted);
  }
}
