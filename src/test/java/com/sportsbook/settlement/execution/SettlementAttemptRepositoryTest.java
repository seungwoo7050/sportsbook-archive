package com.sportsbook.settlement.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.protocol.value.Money;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class SettlementAttemptRepositoryTest {

  @Test
  void insertsClaimOnlyFromAPendingBetAndPreservesCompletePlan() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
    SettlementAttemptRepository repository = new SettlementAttemptRepository(jdbc);
    Instant now = Instant.parse("2026-08-22T00:00:00Z");
    SettlementAttempt attempt =
        SettlementAttempt.resolved(
            UUID.randomUUID(),
            UUID.randomUUID(),
            SettlementResult.WON,
            new SettlementMoneyPlan(
                Money.krw(3000),
                Money.krw(26000),
                Money.krw(2000),
                Money.krw(1000),
                Money.krw(24000)),
            SettlementLease.acquire(now, Duration.ofSeconds(30)),
            now);

    assertThat(repository.claimPending(attempt)).isTrue();

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Object[]> parameters = ArgumentCaptor.forClass(Object[].class);
    verify(jdbc).update(sql.capture(), parameters.capture());
    assertThat(sql.getValue()).contains("status = 'PENDING'", "ON CONFLICT (bet_id) DO NOTHING");
    assertThat(parameters.getValue()).containsSequence(3000L, 26000L, 2000L, 1000L, 24000L, "KRW");
    assertThat(parameters.getValue()[12]).isInstanceOf(Timestamp.class);
    assertThat(parameters.getValue()[15]).isInstanceOf(Timestamp.class);
    assertThat(parameters.getValue()[16]).isInstanceOf(Timestamp.class);
    assertThat(parameters.getValue()).endsWith(attempt.betId());
  }
}
