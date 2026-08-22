package com.sportsbook.settlement.correction;

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

class AdminCandidateLockTest {

  @Test
  @SuppressWarnings("unchecked")
  void locksTheCandidateAndReadsItsCurrentAcceptedPredecessor() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    UUID accepted = UUID.randomUUID();
    var candidate =
        new ResultCandidateStore.AdminCandidate(
            UUID.randomUUID(), ResultCandidateState.PENDING, Instant.EPOCH, accepted, accepted);
    when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
        .thenReturn(List.of(candidate));

    assertThat(new ResultCandidateStore(jdbc).lockForAdmin(UUID.randomUUID())).contains(candidate);

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    verify(jdbc).query(sql.capture(), any(RowMapper.class), any(Object[].class));
    assertThat(sql.getValue())
        .contains(
            "left join match_result",
            "replaces_candidate_id",
            "accepted_candidate_id",
            "for update of c");
  }
}
