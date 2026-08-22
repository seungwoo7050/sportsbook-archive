package com.sportsbook.settlement.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.protocol.value.Money;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SettlementRecoveryRowTest {

  @Test
  void restoresTheImmutableAttemptAndIncrementsTheClaimCount() throws Exception {
    ResultSet row = mock(ResultSet.class);
    UUID betId = UUID.randomUUID();
    UUID eventId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    Instant createdAt = Instant.parse("2026-08-22T00:00:00Z");
    when(row.getObject("bet_id", UUID.class)).thenReturn(betId);
    when(row.getString("action")).thenReturn("SETTLE");
    when(row.getObject("event_id", UUID.class)).thenReturn(eventId);
    when(row.getString("result")).thenReturn("WON");
    when(row.getString("currency")).thenReturn("KRW");
    when(row.getLong("committed_amount")).thenReturn(100L);
    when(row.getLong("payout_amount")).thenReturn(240L);
    when(row.getLong("locked_release_amount")).thenReturn(100L);
    when(row.getLong("house_profit_amount")).thenReturn(140L);
    when(row.getInt("attempt_count")).thenReturn(2);
    when(row.getTimestamp("created_at")).thenReturn(Timestamp.from(createdAt));
    when(row.getObject("user_id", UUID.class)).thenReturn(userId);
    SettlementLease lease =
        new SettlementLease(UUID.randomUUID(), Instant.parse("2026-08-22T00:01:00Z"));
    Instant updatedAt = Instant.parse("2026-08-22T00:00:30Z");

    SettlementExecution execution = SettlementRecoveryRow.read(row).execution(lease, updatedAt);

    assertThat(execution.userId()).isEqualTo(userId);
    assertThat(execution.attempt().betId()).isEqualTo(betId);
    assertThat(execution.attempt().eventId()).isEqualTo(eventId);
    assertThat(execution.attempt().result()).isEqualTo(SettlementResult.WON);
    assertThat(execution.attempt().money().committed()).isEqualTo(Money.krw(100));
    assertThat(execution.attempt().money().payout()).isEqualTo(Money.krw(240));
    assertThat(execution.attempt().lease()).isEqualTo(lease);
    assertThat(execution.attempt().attemptCount()).isEqualTo(3);
    assertThat(execution.attempt().createdAt()).isEqualTo(createdAt);
    assertThat(execution.attempt().updatedAt()).isEqualTo(updatedAt);
  }
}
