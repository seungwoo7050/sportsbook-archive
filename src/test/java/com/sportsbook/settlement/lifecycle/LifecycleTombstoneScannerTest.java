package com.sportsbook.settlement.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sportsbook.protocol.event.EventLifecycleStatus;
import com.sportsbook.settlement.config.SettlementRuntimeProperties;
import com.sportsbook.settlement.config.SettlementWorkerConfiguration;
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
    LifecycleTombstoneScanner scanner = new LifecycleTombstoneScanner(store, fanout, runtime);
    LifecycleObservation first = tombstone(EventLifecycleStatus.CANCELLED, 1);
    LifecycleObservation second = tombstone(EventLifecycleStatus.POSTPONED, 2);
    when(store.findActionableTombstones(25)).thenReturn(List.of(first, second));

    assertThat(scanner.scan()).isEqualTo(2);

    verify(fanout).fanOut(first);
    verify(fanout).fanOut(second);
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
