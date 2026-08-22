package com.sportsbook.settlement.execution;

import com.sportsbook.settlement.config.SettlementRuntimeProperties;
import com.sportsbook.settlement.config.SettlementWorkerConfiguration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SettlementAttemptRecovery {

  private final SettlementAttemptRepository attempts;
  private final SettlementExecutionRunner runner;
  private final SettlementRuntimeProperties runtime;

  public SettlementAttemptRecovery(
      SettlementAttemptRepository attempts,
      SettlementExecutionRunner runner,
      SettlementRuntimeProperties runtime) {
    this.attempts = attempts;
    this.runner = runner;
    this.runtime = runtime;
  }

  @Scheduled(
      fixedDelayString = "${settlement.runtime.recovery-interval:PT1S}",
      initialDelayString = "${settlement.runtime.recovery-interval:PT1S}",
      scheduler = SettlementWorkerConfiguration.RECOVERY)
  public SettlementExecutionRunner.BatchResult recover() {
    var claimed = attempts.claimRecoveryBatch(runtime.leaseDuration(), runtime.batchSize());
    return runner.fanOut(claimed);
  }
}
