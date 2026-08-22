package com.sportsbook.settlement.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.protocol.value.Money;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class InitialAttemptClaimTest {

  @Test
  @SuppressWarnings("unchecked")
  void takesLeaseAndAuditTimesFromOneDatabaseStatement() throws Exception {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    Instant databaseNow = Instant.parse("2026-08-22T00:00:00Z");
    ResultSet result = mock(ResultSet.class);
    when(result.getTimestamp("lease_until"))
        .thenReturn(Timestamp.from(databaseNow.plusSeconds(30)));
    when(result.getTimestamp("created_at")).thenReturn(Timestamp.from(databaseNow));
    when(result.getTimestamp("updated_at")).thenReturn(Timestamp.from(databaseNow));
    when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
        .thenAnswer(
            invocation -> List.of(invocation.<RowMapper<Object>>getArgument(1).mapRow(result, 0)));
    SettlementAttemptDraft draft =
        SettlementAttemptDraft.resolved(
            UUID.randomUUID(), UUID.randomUUID(), SettlementResult.WON, money());

    SettlementAttempt claimed =
        new SettlementAttemptRepository(jdbc)
            .claimPending(draft, Duration.ofSeconds(30))
            .orElseThrow();

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    verify(jdbc).query(sql.capture(), any(RowMapper.class), any(Object[].class));
    assertThat(sql.getValue())
        .contains("current_timestamp + (? * interval '1 millisecond')")
        .contains("1, null, current_timestamp, current_timestamp")
        .contains("returning lease_until, created_at, updated_at");
    assertThat(claimed.createdAt()).isEqualTo(databaseNow);
    assertThat(claimed.lease().until()).isEqualTo(databaseNow.plusSeconds(30));
    assertThat(claimed.attemptCount()).isEqualTo(1);
  }

  private static SettlementMoneyPlan money() {
    return new SettlementMoneyPlan(
        Money.krw(100), Money.krw(200), Money.krw(100), Money.krw(0), Money.krw(100));
  }
}
