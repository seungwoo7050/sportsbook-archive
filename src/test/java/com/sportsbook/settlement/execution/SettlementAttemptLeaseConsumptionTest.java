package com.sportsbook.settlement.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.protocol.value.Money;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class SettlementAttemptLeaseConsumptionTest {

  @Test
  @SuppressWarnings("unchecked")
  void consumesOnlyAnUnexpiredOwnerAndReturnsDatabaseTime() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    Instant databaseNow = Instant.parse("2026-08-22T00:00:00Z");
    when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
        .thenReturn(List.of(databaseNow));
    SettlementAttempt attempt = attempt();

    assertThat(new SettlementAttemptRepository(jdbc).consumeLease(attempt)).contains(databaseNow);

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    verify(jdbc).query(sql.capture(), any(RowMapper.class), any(Object[].class));
    assertThat(sql.getValue())
        .contains(
            "action = ?",
            "lease_token = ?",
            "lease_until > current_timestamp",
            "returning date_trunc('milliseconds', current_timestamp)");
  }

  private static SettlementAttempt attempt() {
    return SettlementAttempt.resolved(
        UUID.randomUUID(),
        UUID.randomUUID(),
        SettlementResult.WON,
        new SettlementMoneyPlan(
            Money.krw(100), Money.krw(200), Money.krw(100), Money.krw(0), Money.krw(100)),
        new SettlementLease(UUID.randomUUID(), Instant.MAX),
        Instant.EPOCH);
  }
}
