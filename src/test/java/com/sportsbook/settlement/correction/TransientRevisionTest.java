package com.sportsbook.settlement.correction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sportsbook.settlement.client.WalletFailurePolicy;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class TransientRevisionTest {

  @Test
  void schedulesDatabaseTimedRetryWithoutChangingPendingState() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
        .thenReturn(List.of(RevisionState.PENDING));
    RevisionLease lease = new RevisionLease(UUID.randomUUID(), Instant.EPOCH.plusSeconds(30));

    assertThat(
            new RevisionPlanRepository(jdbc)
                .releaseTransient(UUID.randomUUID(), lease, WalletFailurePolicy.malformedSuccess()))
        .contains(RevisionState.PENDING);

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Object[]> values = ArgumentCaptor.forClass(Object[].class);
    verify(jdbc).query(sql.capture(), any(RowMapper.class), values.capture());
    assertThat(sql.getValue())
        .contains(
            "attempt_count >= 12",
            "then 'EXHAUSTED'",
            "WALLET_RETRY_EXHAUSTED",
            "current_timestamp",
            "interval '300 seconds'",
            "power(2",
            "then null",
            "lease_token = null",
            "lease_token = ?",
            "lease_until > current_timestamp",
            "returning state");
    assertThat(values.getValue()[0]).isEqualTo("WALLET_MALFORMED_RESPONSE");
    assertThat(values.getValue()[2]).isEqualTo(lease.token());
  }
}
