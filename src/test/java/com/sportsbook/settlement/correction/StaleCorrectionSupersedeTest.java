package com.sportsbook.settlement.correction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sportsbook.settlement.result.MatchOutcomeMode;
import com.sportsbook.settlement.result.MatchResultRecord;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class StaleCorrectionSupersedeTest {

  @Test
  void supersedesAReplacementThatLosesTheSnapshotRace() {
    ResultCandidateStore store = mock(ResultCandidateStore.class);
    when(store.findAcceptedCandidate(any()))
        .thenReturn(
            Optional.of(
                new ResultCandidateStore.AcceptedCandidate(UUID.randomUUID(), Instant.EPOCH)));
    when(store.record(any()))
        .thenAnswer(
            call -> {
              ResultCandidate candidate = call.getArgument(0);
              return new ResultCandidateStore.RecordOutcome(
                  ResultCandidateStore.RecordKind.CREATED,
                  candidate.candidateId(),
                  ResultCandidateState.PENDING);
            });
    when(store.replaceAccepted(any(), any(), any())).thenReturn(false);
    when(store.supersedeStale(any(), any())).thenReturn(true);
    MatchResultRecord result =
        new MatchResultRecord(
            UUID.randomUUID(),
            MatchOutcomeMode.COMPLETED,
            Map.of(),
            Instant.EPOCH,
            Instant.EPOCH.plusSeconds(1));

    assertThat(new ResultCandidateIntake(store).ingest(result))
        .isEqualTo(ResultCandidateIntake.IntakeResult.CORRECTION_SUPERSEDED);
    verify(store).supersedeStale(any(), any());
  }

  @Test
  void staleTransitionOnlyChangesPendingCandidates() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

    assertThat(new ResultCandidateStore(jdbc).supersedeStale(UUID.randomUUID(), Instant.EPOCH))
        .isTrue();

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Object[]> parameters = ArgumentCaptor.forClass(Object[].class);
    verify(jdbc).update(sql.capture(), parameters.capture());
    assertThat(sql.getValue())
        .contains("state = 'SUPERSEDED'", "decision_reason = 'STALE_BASE'", "state = 'PENDING'");
    assertThat(parameters.getValue()[0]).isInstanceOf(Timestamp.class);
  }
}
