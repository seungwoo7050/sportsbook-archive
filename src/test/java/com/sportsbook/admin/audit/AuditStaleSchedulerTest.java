package com.sportsbook.admin.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sportsbook.admin.security.AdminRole;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuditStaleSchedulerTest {

  @Mock private AuditWriteRepository auditWrites;
  @Mock private AdminActionPublisher publisher;

  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();

  @Test
  void claimsTheConfiguredBatchAndCountsEveryTransition() {
    when(auditWrites.claimStale(Duration.ofMinutes(5), 37))
        .thenReturn(List.of(terminal(81), terminal(82)));
    AuditStaleScheduler scheduler =
        new AuditStaleScheduler(auditWrites, publisher, meters, Duration.ofMinutes(5), 37);

    scheduler.scan();

    verify(auditWrites).claimStale(Duration.ofMinutes(5), 37);
    verify(publisher).publish(terminal(81));
    verify(publisher).publish(terminal(82));
    assertThat(meters.counter("admin.audit.stale.claimed").count()).isEqualTo(2);
  }

  @Test
  void recordsAFailureWithoutStoppingTheNextScan() {
    when(auditWrites.claimStale(Duration.ofMinutes(5), 100))
        .thenThrow(new IllegalStateException("database unavailable"))
        .thenReturn(List.of(terminal(83)));
    AuditStaleScheduler scheduler =
        new AuditStaleScheduler(auditWrites, publisher, meters, Duration.ofMinutes(5), 100);

    scheduler.scan();
    scheduler.scan();

    assertThat(meters.counter("admin.audit.stale.scan.failure").count()).isEqualTo(1);
    assertThat(meters.counter("admin.audit.stale.claimed").count()).isEqualTo(1);
    verify(publisher).publish(terminal(83));
  }

  private static AuditTerminalRecord terminal(int suffix) {
    Instant startedAt = Instant.parse("2026-08-22T00:00:00Z");
    return new AuditTerminalRecord(
        UUID.fromString("018f0000-0000-7000-8000-0000000000" + suffix),
        "operator-1",
        AdminRole.ADMIN,
        "MARKET_CLOSE",
        "market-1",
        AuditOutcome.UNKNOWN,
        null,
        "stale",
        "trace-1",
        startedAt,
        startedAt.plus(Duration.ofMinutes(6)));
  }
}
