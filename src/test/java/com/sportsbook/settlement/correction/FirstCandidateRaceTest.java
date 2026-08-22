package com.sportsbook.settlement.correction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sportsbook.settlement.result.MatchOutcomeMode;
import com.sportsbook.settlement.result.MatchResultRecord;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class FirstCandidateRaceTest {

  @Test
  void supersedesTheFirstCandidateThatLosesConcurrentAcceptance() throws Exception {
    ResultCandidateStore store = mock(ResultCandidateStore.class);
    CyclicBarrier bothObservedNoCurrent = new CyclicBarrier(2);
    when(store.findAcceptedCandidate(any()))
        .thenAnswer(
            call -> {
              bothObservedNoCurrent.await();
              return Optional.empty();
            });
    when(store.record(any()))
        .thenAnswer(
            call -> {
              ResultCandidate candidate = call.getArgument(0);
              return new ResultCandidateStore.RecordOutcome(
                  ResultCandidateStore.RecordKind.CREATED,
                  candidate.candidateId(),
                  ResultCandidateState.PENDING);
            });
    AtomicBoolean first = new AtomicBoolean(true);
    when(store.acceptFirst(any(), any())).thenAnswer(call -> first.getAndSet(false));
    when(store.supersedeStale(any(), any())).thenReturn(true);
    UUID eventId = UUID.randomUUID();
    ResultCandidateIntake intake = new ResultCandidateIntake(store);
    var executor = Executors.newFixedThreadPool(2);

    try {
      var one = executor.submit(() -> intake.ingest(result(eventId, MatchOutcomeMode.COMPLETED)));
      var two = executor.submit(() -> intake.ingest(result(eventId, MatchOutcomeMode.VOIDED)));

      assertThat(List.of(one.get(), two.get()))
          .containsExactlyInAnyOrder(
              ResultCandidateIntake.IntakeResult.FIRST_ACCEPTED,
              ResultCandidateIntake.IntakeResult.CORRECTION_SUPERSEDED);
    } finally {
      executor.shutdownNow();
    }
  }

  private MatchResultRecord result(UUID eventId, MatchOutcomeMode mode) {
    return new MatchResultRecord(eventId, mode, Map.of(), Instant.EPOCH, Instant.EPOCH);
  }
}
