package com.sportsbook.settlement.correction;

import com.sportsbook.settlement.config.SettlementRuntimeProperties;
import com.sportsbook.settlement.config.SettlementWorkerConfiguration;
import com.sportsbook.settlement.observability.SettlementMetrics;
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
  private final SettlementMetrics metrics;

  public RevisionRecoveryScanner(
      RevisionRecoveryRepository recovery,
      RevisionPlanReader plans,
      RevisionExecutionRunner runner,
      SettlementRuntimeProperties runtime,
      SettlementMetrics metrics) {
    this.recovery = recovery;
    this.plans = plans;
    this.runner = runner;
    this.runtime = runtime;
    this.metrics = metrics;
  }

  @Scheduled(
      fixedDelayString = "${settlement.runtime.recovery-interval:PT1S}",
      initialDelayString = "${settlement.runtime.recovery-interval:PT1S}",
      scheduler = SettlementWorkerConfiguration.REVISION_RECOVERY)
  public List<RevisionExecutionRunner.Result> recover() {
    var sample = metrics.start();
    try {
      var claims = recovery.claimDue(runtime.leaseDuration(), runtime.batchSize());
      List<RevisionExecutionRunner.Result> results = new ArrayList<>(claims.size());
      for (var claim : claims) {
        RevisionPlan plan =
            plans
                .find(claim.revisionId())
                .orElseThrow(() -> new IllegalStateException("Claimed revision plan is missing"));
        var result = runner.execute(plan, claim.lease(), true, !claim.blockedProof());
        results.add(result);
        metrics.count("revision", result.name().toLowerCase(java.util.Locale.ROOT));
      }
      return List.copyOf(results);
    } catch (RuntimeException failure) {
      metrics.count("revision", "failed");
      throw failure;
    } finally {
      metrics.stop(sample, "revision");
    }
  }
}
