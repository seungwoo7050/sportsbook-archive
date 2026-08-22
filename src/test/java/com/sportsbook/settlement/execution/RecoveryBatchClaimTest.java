package com.sportsbook.settlement.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class RecoveryBatchClaimTest {

  @Test
  @SuppressWarnings("unchecked")
  void locksExpiredPendingAttemptsInDeterministicOrder() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
    SettlementAttemptRepository repository = new SettlementAttemptRepository(jdbc);
    assertThat(repository.claimRecoveryBatch(Duration.ofSeconds(30), 25)).isEmpty();

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Object[]> parameters = ArgumentCaptor.forClass(Object[].class);
    verify(jdbc).query(sql.capture(), any(RowMapper.class), parameters.capture());
    assertThat(sql.getValue())
        .contains(
            "a.lease_until <= current_timestamp",
            "order by a.updated_at, a.bet_id",
            "for update of a skip locked");
    assertThat(parameters.getValue()).containsExactly(25);
  }

  @Test
  void rejectsUnboundedRecoveryBatch() {
    SettlementAttemptRepository repository =
        new SettlementAttemptRepository(mock(JdbcTemplate.class));

    assertThatThrownBy(() -> repository.claimRecoveryBatch(Duration.ofSeconds(30), 1001))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
