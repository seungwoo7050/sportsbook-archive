package com.sportsbook.settlement.correction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class RevisionRecoveryRepositoryTest {

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void claimsOnlyDueBoundedWorkWithADatabaseTimedLease() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    UUID revisionId = UUID.randomUUID();
    Instant until = Instant.parse("2026-08-22T00:00:30Z");
    when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
        .thenReturn((List) List.of(revisionId))
        .thenReturn((List) List.of(Timestamp.from(until)));

    List<RevisionRecoveryRepository.Claim> claimed =
        new RevisionRecoveryRepository(jdbc).claimDue(Duration.ofSeconds(30), 25);

    assertThat(claimed).hasSize(1);
    assertThat(claimed.get(0).revisionId()).isEqualTo(revisionId);
    assertThat(claimed.get(0).lease().until()).isEqualTo(until);
    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Object[]> values = ArgumentCaptor.forClass(Object[].class);
    verify(jdbc).update(sql.capture());
    verify(jdbc, org.mockito.Mockito.times(2))
        .query(sql.capture(), any(RowMapper.class), values.capture());
    assertThat(sql.getAllValues().get(0))
        .contains("else 'EXHAUSTED'", "attempt_count >= 12", "lease_until <= current_timestamp");
    assertThat(sql.getAllValues().get(1))
        .contains(
            "attempt_count < 12",
            "next_retry_at <= current_timestamp",
            "lease_until <= current_timestamp",
            "for update skip locked");
    assertThat(sql.getAllValues().get(2))
        .contains(
            "current_timestamp + (? * interval '1 millisecond')",
            "attempt_count = attempt_count + 1",
            "next_retry_at = null",
            "returning lease_until");
    assertThat(values.getAllValues().get(0)).containsExactly(25);
    assertThat(values.getAllValues().get(1))
        .containsExactly(claimed.get(0).lease().token(), 30_000L, revisionId);
  }
}
