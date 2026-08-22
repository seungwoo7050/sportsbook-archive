package com.sportsbook.settlement.correction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class CandidateApprovalTest {

  @Test
  @SuppressWarnings("unchecked")
  void approvesOnlyAPendingCandidateBasedOnTheCurrentSnapshot() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    UUID expectedAccepted = UUID.randomUUID();
    when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
        .thenReturn(List.of(expectedAccepted));
    when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

    assertThat(new ResultCandidateStore(jdbc).approve(UUID.randomUUID(), Instant.EPOCH)).isTrue();

    ArgumentCaptor<String> query = ArgumentCaptor.forClass(String.class);
    verify(jdbc).query(query.capture(), any(RowMapper.class), any(Object[].class));
    assertThat(query.getValue()).contains("state = 'PENDING'", "replaces_candidate_id is not null");

    ArgumentCaptor<String> updates = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Object[]> parameters = ArgumentCaptor.forClass(Object[].class);
    verify(jdbc, times(5)).update(updates.capture(), parameters.capture());
    assertThat(updates.getAllValues().get(0)).contains("m.accepted_candidate_id = ?");
    assertThat(parameters.getAllValues().get(0)).contains(expectedAccepted);
    assertThat(parameters.getAllValues().get(3)[0]).isInstanceOf(Timestamp.class);
    assertThat(parameters.getAllValues().get(4)[0]).isInstanceOf(Timestamp.class);
    assertThat(parameters.getAllValues().get(3)).contains("OPERATOR_APPROVED");
    assertThat(parameters.getAllValues().get(4)).contains("OPERATOR_APPROVED");
  }
}
