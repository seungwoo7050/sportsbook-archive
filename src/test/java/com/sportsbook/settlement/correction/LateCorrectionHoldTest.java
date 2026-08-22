package com.sportsbook.settlement.correction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sportsbook.settlement.result.MatchOutcomeMode;
import com.sportsbook.settlement.result.MatchResultRecord;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LateCorrectionHoldTest {

  @Test
  void holdsCandidatesStrictlyAfterTheCorrectionWindow() {
    ResultCandidateStore store = mock(ResultCandidateStore.class);
    UUID acceptedId = UUID.randomUUID();
    when(store.findAcceptedCandidate(any()))
        .thenReturn(
            Optional.of(new ResultCandidateStore.AcceptedCandidate(acceptedId, Instant.EPOCH)));
    when(store.record(any()))
        .thenAnswer(
            call -> {
              ResultCandidate candidate = call.getArgument(0);
              return new ResultCandidateStore.RecordOutcome(
                  ResultCandidateStore.RecordKind.CREATED,
                  candidate.candidateId(),
                  ResultCandidateState.PENDING);
            });
    MatchResultRecord late = resultAt(Instant.EPOCH.plus(Duration.ofHours(24)).plusNanos(1));

    assertThat(new ResultCandidateIntake(store).ingest(late))
        .isEqualTo(ResultCandidateIntake.IntakeResult.LATE_HELD);

    verify(store, never()).replaceAccepted(any(), any(), any());
    ArgumentCaptor<ResultCandidate> candidate = ArgumentCaptor.forClass(ResultCandidate.class);
    verify(store).record(candidate.capture());
    assertThat(candidate.getValue().replacesCandidateId()).isEqualTo(acceptedId);
  }

  @Test
  void acceptsCandidatesAtTheCorrectionWindowBoundary() {
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
    when(store.replaceAccepted(any(), any(), any())).thenReturn(true);

    assertThat(
            new ResultCandidateIntake(store)
                .ingest(resultAt(Instant.EPOCH.plus(Duration.ofHours(24)))))
        .isEqualTo(ResultCandidateIntake.IntakeResult.AUTO_CORRECTION_ACCEPTED);
  }

  private MatchResultRecord resultAt(Instant receivedAt) {
    return new MatchResultRecord(
        UUID.randomUUID(), MatchOutcomeMode.COMPLETED, Map.of(), Instant.EPOCH, receivedAt);
  }
}
