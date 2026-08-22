package com.sportsbook.settlement.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sportsbook.protocol.event.EventLifecycleStatus;
import com.sportsbook.settlement.config.SettlementRuntimeProperties;
import com.sportsbook.settlement.config.SettlementWorkerConfiguration;
import com.sportsbook.settlement.observability.SettlementMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

class LifecycleTombstoneScannerTest {

  @Test
  void fansOutEveryActionableTombstoneInStoredOrder() {
    LifecycleStore store = mock(LifecycleStore.class);
    LifecycleFanout fanout = mock(LifecycleFanout.class);
    SettlementRuntimeProperties runtime = new SettlementRuntimeProperties(null, null, null, 25);
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    LifecycleTombstoneScanner scanner =
        new LifecycleTombstoneScanner(store, fanout, runtime, new SettlementMetrics(registry));
    LifecycleObservation first = tombstone(EventLifecycleStatus.CANCELLED, 1);
    LifecycleObservation second = tombstone(EventLifecycleStatus.POSTPONED, 2);
    when(store.findActionableTombstones(25)).thenReturn(List.of(first, second));

    assertThat(scanner.scan()).isEqualTo(2);

    verify(fanout).fanOut(first);
    verify(fanout).fanOut(second);
    assertThat(
            registry
                .counter(SettlementMetrics.OPERATIONS, "flow", "lifecycle", "outcome", "processed")
                .count())
        .isEqualTo(2);
    assertThat(registry.timer(SettlementMetrics.DURATION, "flow", "lifecycle").count()).isOne();
  }

  @Test
  void runsOnTheIsolatedLifecycleScheduler() throws NoSuchMethodException {
    Scheduled scheduled =
        LifecycleTombstoneScanner.class.getMethod("scan").getAnnotation(Scheduled.class);

    assertThat(scheduled.scheduler()).isEqualTo(SettlementWorkerConfiguration.LIFECYCLE);
  }

  private static LifecycleObservation tombstone(EventLifecycleStatus status, long second) {
    return LifecycleObservation.observe(
        UUID.randomUUID(),
        status,
        Instant.EPOCH.plusSeconds(second),
        null,
        Instant.EPOCH.plusSeconds(second));
  }
}
