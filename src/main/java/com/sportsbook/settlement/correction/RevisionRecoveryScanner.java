package com.sportsbook.settlement.correction;

import com.sportsbook.settlement.config.SettlementRuntimeProperties;
import com.sportsbook.settlement.config.SettlementWorkerConfiguration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RevisionRecoveryScanner {

  private final RevisionRecoveryRepository recovery;
  private final RevisionPlanReader plans;
  private final RevisionExecutionRunner runner;
  private final SettlementRuntimeProperties runtime;

  public RevisionRecoveryScanner(
      RevisionRecoveryRepository recovery,
      RevisionPlanReader plans,
      RevisionExecutionRunner runner,
      SettlementRuntimeProperties runtime) {
    this.recovery = recovery;
    this.plans = plans;
    this.runner = runner;
    this.runtime = runtime;
  }

  @Scheduled(
      fixedDelayString = "${settlement.runtime.recovery-interval:PT1S}",
      initialDelayString = "${settlement.runtime.recovery-interval:PT1S}",
      scheduler = SettlementWorkerConfiguration.RECOVERY)
  public List<RevisionExecutionRunner.Result> recover() {
    var claims = recovery.claimDue(runtime.leaseDuration(), runtime.batchSize());
    List<RevisionExecutionRunner.Result> results = new ArrayList<>(claims.size());
    for (var claim : claims) {
      RevisionPlan plan =
          plans
              .find(claim.revisionId())
              .orElseThrow(() -> new IllegalStateException("Claimed revision plan is missing"));
      results.add(runner.execute(plan, claim.lease(), true, !claim.blockedProof()));
    }
    return List.copyOf(results);
  }
}
