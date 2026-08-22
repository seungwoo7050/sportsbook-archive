package com.sportsbook.admin.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.admin.event.AdminActionRecorded;
import com.sportsbook.admin.security.AdminRole;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditEventMapperTest {

  @Test
  void preservesEveryTerminalAuditField() {
    Instant startedAt = Instant.parse("2026-08-22T01:02:03.004Z");
    Instant completedAt = Instant.parse("2026-08-22T01:02:08.009Z");
    AuditTerminalRecord record =
        new AuditTerminalRecord(
            UUID.fromString("018f0000-0000-7000-8000-000000000092"),
            "operator-1",
            AdminRole.TRADER,
            "MARKET_SUSPEND",
            "event-1/market-2",
            AuditOutcome.UNKNOWN,
            null,
            "feed timeout",
            "0123456789abcdef0123456789abcdef",
            startedAt,
            completedAt);

    AdminActionRecorded event = AuditEventMapper.toEvent(record);

    assertThat(event.getActionId()).isEqualTo(record.actionId().toString());
    assertThat(event.getActorId()).isEqualTo(record.actorId());
    assertThat(event.getActorRole()).isEqualTo("TRADER");
    assertThat(event.getAction()).isEqualTo("MARKET_SUSPEND");
    assertThat(event.getTarget()).isEqualTo("event-1/market-2");
    assertThat(event.getOutcome()).isEqualTo("UNKNOWN");
    assertThat(event.getHttpStatus()).isNull();
    assertThat(event.getReason()).isEqualTo("feed timeout");
    assertThat(event.getTraceId()).isEqualTo(record.traceId());
    assertThat(event.getStartedAt()).isEqualTo(startedAt);
    assertThat(event.getCompletedAt()).isEqualTo(completedAt);
  }
}
