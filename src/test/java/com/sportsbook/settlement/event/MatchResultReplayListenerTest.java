package com.sportsbook.settlement.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sportsbook.settlement.correction.ResultCandidateIntake;
import com.sportsbook.settlement.result.AcceptedResult;
import com.sportsbook.settlement.result.AcceptedResultRepository;
import com.sportsbook.settlement.result.MatchOutcomeMode;
import com.sportsbook.settlement.result.ResultFanout;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

class MatchResultReplayListenerTest {

  @Test
  void replaysAlreadyAcceptedEvidenceBeforeAcknowledgment() {
    UUID eventId = UUID.randomUUID();
    ResultCandidateIntake intake = mock(ResultCandidateIntake.class);
    AcceptedResultRepository acceptedResults = mock(AcceptedResultRepository.class);
    ResultFanout fanout = mock(ResultFanout.class);
    Acknowledgment acknowledgment = mock(Acknowledgment.class);
    AcceptedResult accepted =
        new AcceptedResult(
            eventId, UUID.randomUUID(), MatchOutcomeMode.VOIDED, Map.of(), Instant.EPOCH);
    when(intake.ingest(any())).thenReturn(ResultCandidateIntake.IntakeResult.ACCEPTED_REPLAY);
    when(acceptedResults.findByEventId(eventId)).thenReturn(Optional.of(accepted));
    MatchResultListener listener =
        new MatchResultListener(
            intake, acceptedResults, fanout, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));

    listener.receive(MatchResultListenerTest.record(eventId), acknowledgment);

    var order = inOrder(intake, acceptedResults, fanout, acknowledgment);
    order.verify(intake).ingest(any());
    order.verify(acceptedResults).findByEventId(eventId);
    order.verify(fanout).fanOut(accepted);
    order.verify(acknowledgment).acknowledge();
  }
}
