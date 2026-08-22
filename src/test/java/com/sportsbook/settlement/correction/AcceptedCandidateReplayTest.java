package com.sportsbook.settlement.correction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sportsbook.settlement.result.MatchOutcomeMode;
import com.sportsbook.settlement.result.MatchResultRecord;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AcceptedCandidateReplayTest {

  @Test
  void distinguishesReplayOfTheAcceptedEvidenceForFanoutRecovery() {
    ResultCandidateStore store = mock(ResultCandidateStore.class);
    UUID candidateId = UUID.randomUUID();
    when(store.findAcceptedCandidate(any()))
        .thenReturn(
            Optional.of(new ResultCandidateStore.AcceptedCandidate(candidateId, Instant.EPOCH)));
    when(store.record(any()))
        .thenReturn(
            new ResultCandidateStore.RecordOutcome(
                ResultCandidateStore.RecordKind.NO_CHANGE,
                candidateId,
                ResultCandidateState.ACCEPTED));
    MatchResultRecord replay =
        new MatchResultRecord(
            UUID.randomUUID(),
            MatchOutcomeMode.COMPLETED,
            Map.of(),
            Instant.EPOCH,
            Instant.EPOCH.plusSeconds(1));

    assertThat(new ResultCandidateIntake(store).ingest(replay))
        .isEqualTo(ResultCandidateIntake.IntakeResult.ACCEPTED_REPLAY);
  }
}
