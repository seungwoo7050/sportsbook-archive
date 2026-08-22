package com.sportsbook.settlement.correction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sportsbook.settlement.result.MatchOutcomeMode;
import com.sportsbook.settlement.result.MatchResultRecord;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class FirstCandidateAcceptanceTest {

  @Test
  void createsCurrentSnapshotBeforeAcceptingCandidate() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1, 1, 1);
    UUID candidateId = UUID.randomUUID();

    assertThat(new ResultCandidateStore(jdbc).acceptFirst(candidateId, Instant.EPOCH)).isTrue();

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Object[]> parameters = ArgumentCaptor.forClass(Object[].class);
    verify(jdbc, times(3)).update(sql.capture(), parameters.capture());
    assertThat(sql.getAllValues().get(0))
        .contains(
            "insert into match_result",
            "settled_at <= current_timestamp",
            "on conflict (event_id) do nothing");
    assertThat(sql.getAllValues().get(1)).contains("insert into match_selection_result");
    assertThat(sql.getAllValues().get(2)).contains("state = 'ACCEPTED'", "decision_reason = ?");
    assertThat(parameters.getAllValues().get(2)[0]).isInstanceOf(Timestamp.class);
    assertThat(parameters.getAllValues().get(2)).contains("FIRST_RESULT");
  }

  @Test
  void recordsOperatorApprovalForTheFirstCandidate() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1, 1, 1);

    assertThat(new ResultCandidateStore(jdbc).approveFirst(UUID.randomUUID(), Instant.EPOCH))
        .isTrue();

    ArgumentCaptor<Object[]> parameters = ArgumentCaptor.forClass(Object[].class);
    verify(jdbc, times(3)).update(anyString(), parameters.capture());
    assertThat(parameters.getAllValues().get(2)).contains("OPERATOR_APPROVED");
  }

  @Test
  void intakeClassifiesTheFirstAcceptedSnapshot() {
    ResultCandidateStore store = mock(ResultCandidateStore.class);
    when(store.record(any()))
        .thenAnswer(
            invocation -> {
              ResultCandidate candidate = invocation.getArgument(0);
              return new ResultCandidateStore.RecordOutcome(
                  ResultCandidateStore.RecordKind.CREATED,
                  candidate.candidateId(),
                  ResultCandidateState.PENDING);
            });
    when(store.acceptFirst(any(), any())).thenReturn(true);
    Instant receivedAt = Instant.EPOCH.plusSeconds(1);
    MatchResultRecord result =
        new MatchResultRecord(
            UUID.randomUUID(), MatchOutcomeMode.COMPLETED, Map.of(), Instant.EPOCH, receivedAt);

    assertThat(new ResultCandidateIntake(store).ingest(result))
        .isEqualTo(ResultCandidateIntake.IntakeResult.FIRST_ACCEPTED);
  }

  @Test
  void failsTheTransactionWhenCandidateAcceptanceIsLost() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1, 1, 0);

    assertThatIllegalStateException()
        .isThrownBy(
            () -> new ResultCandidateStore(jdbc).acceptFirst(UUID.randomUUID(), Instant.EPOCH));
  }
}
