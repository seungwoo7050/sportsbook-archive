package com.sportsbook.settlement.execution;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SettlementExecutionRunner {

  private final SettlementWalletExecutor wallet;
  private final SettlementFinalizer finalizer;
  private final SettlementAttemptRepository attempts;
  private final Clock clock;

  public SettlementExecutionRunner(
      SettlementWalletExecutor wallet,
      SettlementFinalizer finalizer,
      SettlementAttemptRepository attempts,
      Clock clock) {
    this.wallet = wallet;
    this.finalizer = finalizer;
    this.attempts = attempts;
    this.clock = clock;
  }

  public void execute(SettlementExecution execution) {
    SettlementAttempt attempt = execution.attempt();
    try {
      wallet.releaseLocked(attempt, execution.userId());
      wallet.forfeitLocked(attempt, execution.userId());
      wallet.payHouseProfit(attempt, execution.userId());
      Instant now = clock.instant();
      boolean finalized =
          attempt.action() == SettlementAttempt.Action.SETTLE
              ? finalizer.settle(attempt, now)
              : finalizer.voidBet(attempt, now);
      if (!finalized) {
        throw new IllegalStateException("Settlement lease was lost before finalization");
      }
    } catch (RuntimeException failure) {
      attempts.releaseForRecovery(attempt, failure, clock.instant());
      throw failure;
    }
  }

  public BatchResult fanOut(List<SettlementExecution> executions) {
    int succeeded = 0;
    for (SettlementExecution execution : executions) {
      try {
        execute(execution);
        succeeded++;
      } catch (RuntimeException ignored) {
        // Every failure was durably released above; other independent bets must continue.
      }
    }
    return new BatchResult(succeeded, executions.size() - succeeded);
  }

  public record BatchResult(int succeeded, int failed) {}
}
