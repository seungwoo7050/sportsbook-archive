package com.sportsbook.settlement.persistence;

import com.sportsbook.settlement.execution.SettlementAttemptRepository;
import com.sportsbook.settlement.execution.SettlementExecution;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

final class RecoveryClaimLoadHarness {

  private RecoveryClaimLoadHarness() {}

  static List<SettlementExecution> claim(
      SettlementAttemptRepository attempts, int workers, int batchSize) throws Exception {
    var start = new CyclicBarrier(workers);
    var executor = Executors.newFixedThreadPool(workers);
    List<Future<List<SettlementExecution>>> futures = new ArrayList<>(workers);
    try {
      for (int worker = 0; worker < workers; worker++) {
        futures.add(
            executor.submit(
                () -> {
                  start.await(10, TimeUnit.SECONDS);
                  return attempts.claimRecoveryBatch(Duration.ofSeconds(30), batchSize);
                }));
      }
      List<SettlementExecution> claims = new ArrayList<>(workers * batchSize);
      for (Future<List<SettlementExecution>> future : futures) {
        claims.addAll(future.get(30, TimeUnit.SECONDS));
      }
      return List.copyOf(claims);
    } finally {
      executor.shutdownNow();
      if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Recovery load workers did not stop");
      }
    }
  }
}
