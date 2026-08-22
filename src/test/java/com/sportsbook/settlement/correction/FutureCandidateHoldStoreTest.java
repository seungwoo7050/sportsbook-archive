package com.sportsbook.settlement.correction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class FutureCandidateHoldStoreTest {

  @Test
  @SuppressWarnings("unchecked")
  void recordsAnOperatorVisibleHoldUsingDatabaseTime() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    UUID candidateId = UUID.randomUUID();
    when(jdbc.query(any(String.class), any(RowMapper.class), eq(candidateId)))
        .thenReturn(List.of(true));

    assertThat(new ResultCandidateStore(jdbc).holdWhileFuture(candidateId)).isTrue();

    ArgumentCaptor<String> statement = ArgumentCaptor.forClass(String.class);
    verify(jdbc).query(statement.capture(), any(RowMapper.class), eq(candidateId));
    assertThat(statement.getValue())
        .contains("settled_at > current_timestamp", "'FUTURE_HELD'", "else null");
  }
}
