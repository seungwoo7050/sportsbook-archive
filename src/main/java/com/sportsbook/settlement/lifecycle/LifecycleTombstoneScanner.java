package com.sportsbook.settlement.lifecycle;

import com.sportsbook.settlement.config.SettlementRuntimeProperties;
import com.sportsbook.settlement.config.SettlementWorkerConfiguration;
import com.sportsbook.settlement.observability.SettlementMetrics;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class LifecycleTombstoneScanner {

  private final LifecycleStore store;
  private final LifecycleFanout fanout;
  private final SettlementRuntimeProperties runtime;
  private final SettlementMetrics metrics;

  public LifecycleTombstoneScanner(
      LifecycleStore store,
      LifecycleFanout fanout,
      SettlementRuntimeProperties runtime,
      SettlementMetrics metrics) {
    this.store = store;
    this.fanout = fanout;
    this.runtime = runtime;
    this.metrics = metrics;
  }

  @Scheduled(
      fixedDelayString = "${settlement.runtime.recovery-interval:PT1S}",
      initialDelayString = "${settlement.runtime.recovery-interval:PT1S}",
      scheduler = SettlementWorkerConfiguration.LIFECYCLE)
  public int scan() {
    var sample = metrics.start();
    try {
      var tombstones = store.findActionableTombstones(runtime.batchSize());
      tombstones.forEach(fanout::fanOut);
      metrics.count("lifecycle", "processed", tombstones.size());
      return tombstones.size();
    } catch (RuntimeException failure) {
      metrics.count("lifecycle", "failed");
      throw failure;
    } finally {
      metrics.stop(sample, "lifecycle");
    }
  }
}
