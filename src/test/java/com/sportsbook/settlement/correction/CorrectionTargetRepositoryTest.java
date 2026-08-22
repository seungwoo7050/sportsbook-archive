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

class CorrectionTargetRepositoryTest {

  @Test
  @SuppressWarnings("unchecked")
  void boundsAndExcludesBetsWithAnOwnedNextRevision() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    UUID eventId = UUID.randomUUID();
    UUID candidateId = UUID.randomUUID();
    UUID betId = UUID.randomUUID();
    when(jdbc.query(any(String.class), any(RowMapper.class), eq(eventId), eq(candidateId), eq(100)))
        .thenReturn(List.of(betId));

    assertThat(new CorrectionTargetRepository(jdbc).findActionable(eventId, candidateId, 100))
        .containsExactly(betId);

    ArgumentCaptor<String> statement = ArgumentCaptor.forClass(String.class);
    verify(jdbc)
        .query(statement.capture(), any(RowMapper.class), eq(eventId), eq(candidateId), eq(100));
    assertThat(statement.getValue())
        .contains(
            "b.status = 'SETTLED'",
            "s.source_candidate_id <> ?",
            "r.revision_number = b.revision_number + 1",
            "order by b.bet_id limit ?");
  }
}
