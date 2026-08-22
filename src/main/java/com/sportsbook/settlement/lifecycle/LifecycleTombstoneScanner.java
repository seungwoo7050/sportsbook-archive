package com.sportsbook.settlement.lifecycle;

import com.sportsbook.settlement.config.SettlementRuntimeProperties;
import com.sportsbook.settlement.config.SettlementWorkerConfiguration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class LifecycleTombstoneScanner {

  private final LifecycleStore store;
  private final LifecycleFanout fanout;
  private final SettlementRuntimeProperties runtime;

  public LifecycleTombstoneScanner(
      LifecycleStore store, LifecycleFanout fanout, SettlementRuntimeProperties runtime) {
    this.store = store;
    this.fanout = fanout;
    this.runtime = runtime;
  }

  @Scheduled(
      fixedDelayString = "${settlement.runtime.recovery-interval:PT1S}",
      initialDelayString = "${settlement.runtime.recovery-interval:PT1S}",
      scheduler = SettlementWorkerConfiguration.LIFECYCLE)
  public int scan() {
    var tombstones = store.findActionableTombstones(runtime.batchSize());
    tombstones.forEach(fanout::fanOut);
    return tombstones.size();
  }
}
