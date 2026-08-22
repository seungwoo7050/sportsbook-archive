package com.sportsbook.admin.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sportsbook.admin.security.AdminRole;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class AuditServicePublishingTest {

  private static final UUID ACTION_ID = UUID.fromString("018f0000-0000-7000-8000-000000000095");

  @Test
  void publishesOnlyAfterTheGuardedTerminalUpdate() {
    AuditWriteRepository writes = mock(AuditWriteRepository.class);
    AdminActionPublisher publisher = mock(AdminActionPublisher.class);
    AuditTerminalRecord terminal = terminal();
    when(writes.complete(ACTION_ID, AuditOutcome.SUCCESS, 202)).thenReturn(terminal);
    AuditService service = new AuditService(writes, publisher);

    AuditTerminalRecord result = service.complete(ACTION_ID, AuditOutcome.SUCCESS, 202);

    assertThat(result).isSameAs(terminal);
    InOrder lifecycle = inOrder(writes, publisher);
    lifecycle.verify(writes).complete(ACTION_ID, AuditOutcome.SUCCESS, 202);
    lifecycle.verify(publisher).publish(terminal);
  }

  private static AuditTerminalRecord terminal() {
    Instant started = Instant.parse("2026-08-22T01:02:03Z");
    return new AuditTerminalRecord(
        ACTION_ID,
        "operator-1",
        AdminRole.ADMIN,
        "MARKET_CLOSE",
        "market-1",
        AuditOutcome.SUCCESS,
        202,
        "operator request",
        "trace-1",
        started,
        started.plusSeconds(1));
  }
}
