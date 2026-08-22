package com.sportsbook.settlement.correction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sportsbook.settlement.result.MatchOutcomeMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class ResultCandidateReplayTest {

  @Test
  @SuppressWarnings("unchecked")
  void pendingSemanticIdentityIsAnExactReplay() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    ResultCandidate candidate = candidate();
    ResultCandidateStore.RecordOutcome existing =
        new ResultCandidateStore.RecordOutcome(
            ResultCandidateStore.RecordKind.EXACT_REPLAY,
            candidate.candidateId(),
            ResultCandidateState.PENDING);
    when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
        .thenReturn(List.of(existing));

    assertThat(new ResultCandidateStore(jdbc).record(candidate)).isEqualTo(existing);
    verify(jdbc, never()).update(anyString(), any(Object[].class));
  }

  @Test
  @SuppressWarnings("unchecked")
  void previouslyDecidedSemanticIdentityIsNoChange() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    ResultCandidate candidate = candidate();
    ResultCandidateStore.RecordOutcome existing =
        new ResultCandidateStore.RecordOutcome(
            ResultCandidateStore.RecordKind.NO_CHANGE,
            candidate.candidateId(),
            ResultCandidateState.ACCEPTED);
    when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
        .thenReturn(List.of(existing));

    assertThat(new ResultCandidateStore(jdbc).record(candidate).kind())
        .isEqualTo(ResultCandidateStore.RecordKind.NO_CHANGE);
  }

  @Test
  @SuppressWarnings("unchecked")
  void newEvidenceUsesTypedTimestampParameters() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
    when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

    new ResultCandidateStore(jdbc).record(candidate());

    ArgumentCaptor<Object[]> parameters = ArgumentCaptor.forClass(Object[].class);
    verify(jdbc).update(anyString(), parameters.capture());
    assertThat(parameters.getValue()[4]).isInstanceOf(Timestamp.class);
    assertThat(parameters.getValue()[5]).isInstanceOf(Timestamp.class);
  }

  private static ResultCandidate candidate() {
    return ResultCandidate.pending(
        UUID.randomUUID(),
        "b".repeat(64),
        MatchOutcomeMode.COMPLETED,
        Map.of(),
        Instant.EPOCH,
        Instant.EPOCH.plusSeconds(1),
        null);
  }
}
