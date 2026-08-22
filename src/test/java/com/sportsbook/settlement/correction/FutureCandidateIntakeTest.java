package com.sportsbook.settlement.correction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sportsbook.settlement.result.MatchOutcomeMode;
import com.sportsbook.settlement.result.MatchResultRecord;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FutureCandidateIntakeTest {

  @Test
  void futureEvidenceRemainsPendingWithoutChangingTheAcceptedSnapshot() {
    ResultCandidateStore store = mock(ResultCandidateStore.class);
    UUID candidateId = UUID.randomUUID();
    when(store.findAcceptedCandidate(any())).thenReturn(Optional.empty());
    when(store.record(any()))
        .thenReturn(
            new ResultCandidateStore.RecordOutcome(
                ResultCandidateStore.RecordKind.CREATED,
                candidateId,
                ResultCandidateState.PENDING,
                Instant.EPOCH));
    when(store.holdWhileFuture(candidateId)).thenReturn(true);

    assertThat(new ResultCandidateIntake(store).ingest(result(Instant.EPOCH)))
        .isEqualTo(ResultCandidateIntake.IntakeResult.FUTURE_HELD);

    verify(store, never()).acceptFirst(any(), any());
    verify(store, never()).replaceAccepted(any(), any(), any());
  }

  @Test
  void dueReplayReentersDecisionWithItsDurableCandidateIdentity() {
    ResultCandidateStore store = mock(ResultCandidateStore.class);
    UUID candidateId = UUID.randomUUID();
    Instant firstReceivedAt = Instant.EPOCH.plusSeconds(1);
    Instant replayedAt = Instant.EPOCH.plusSeconds(5);
    when(store.findAcceptedCandidate(any())).thenReturn(Optional.empty());
    when(store.record(any()))
        .thenReturn(
            new ResultCandidateStore.RecordOutcome(
                ResultCandidateStore.RecordKind.EXACT_REPLAY,
                candidateId,
                ResultCandidateState.PENDING,
                firstReceivedAt));
    when(store.acceptFirst(candidateId, replayedAt)).thenReturn(true);

    assertThat(new ResultCandidateIntake(store).ingest(result(replayedAt)))
        .isEqualTo(ResultCandidateIntake.IntakeResult.FIRST_ACCEPTED);

    verify(store).acceptFirst(candidateId, replayedAt);
  }

  private static MatchResultRecord result(Instant receivedAt) {
    return new MatchResultRecord(
        UUID.randomUUID(), MatchOutcomeMode.COMPLETED, Map.of(), Instant.EPOCH, receivedAt);
  }
}
