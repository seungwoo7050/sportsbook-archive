package com.sportsbook.settlement.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class AdminRevisionRetryRepositoryTest {

  @Test
  @SuppressWarnings("unchecked")
  void queuesOnlyPausedWorkWithoutErasingWalletProof() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    UUID revisionId = UUID.randomUUID();
    var queued = new AdminRevisionRetryRepository.Queued("BLOCKED", true, Instant.MAX);
    when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
        .thenReturn(List.of(queued));
    AdminRevisionRetryRepository retries = new AdminRevisionRetryRepository(jdbc);

    assertThat(retries.queue(revisionId)).contains(queued);

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    verify(jdbc).query(sql.capture(), any(RowMapper.class), any(Object[].class));
    assertThat(sql.getValue())
        .contains(
            "wallet_status = 'BLOCKED' then 'BLOCKED' else 'PENDING'",
            "attempt_count = 0",
            "current_timestamp",
            "greatest(current_timestamp, wallet_next_attempt_at)",
            "state = 'EXHAUSTED'",
            "state = 'BLOCKED'",
            "wallet_status = 'BLOCKED'",
            "last_error_code is not null")
        .doesNotContain("wallet_status = null", "wallet_queue_sequence = null");
  }
}
