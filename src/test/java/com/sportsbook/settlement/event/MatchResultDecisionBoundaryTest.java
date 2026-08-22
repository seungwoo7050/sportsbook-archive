package com.sportsbook.settlement.event;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sportsbook.settlement.correction.ResultCandidateIntake;
import com.sportsbook.settlement.result.AcceptedResultRepository;
import com.sportsbook.settlement.result.ResultFanout;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

class MatchResultDecisionBoundaryTest {

  private final ResultCandidateIntake intake = mock(ResultCandidateIntake.class);
  private final AcceptedResultRepository accepted = mock(AcceptedResultRepository.class);
  private final ResultFanout fanout = mock(ResultFanout.class);
  private final Acknowledgment acknowledgment = mock(Acknowledgment.class);
  private final MatchResultListener listener =
      new MatchResultListener(intake, accepted, fanout, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));

  @Test
  void acknowledgesHeldOrSupersededEvidenceWithoutBaseFanout() {
    when(intake.ingest(any())).thenReturn(ResultCandidateIntake.IntakeResult.NO_CHANGE);

    listener.receive(MatchResultListenerTest.record(UUID.randomUUID()), acknowledgment);

    var order = inOrder(intake, acknowledgment);
    order.verify(intake).ingest(any());
    order.verify(acknowledgment).acknowledge();
    verifyNoInteractions(accepted, fanout);
  }

  @Test
  void retriesWhenAFirstAcceptanceHasNoAuthoritativeProjection() {
    UUID eventId = UUID.randomUUID();
    when(intake.ingest(any())).thenReturn(ResultCandidateIntake.IntakeResult.FIRST_ACCEPTED);
    when(accepted.findByEventId(eventId)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> listener.receive(MatchResultListenerTest.record(eventId), acknowledgment))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("projection");
    verifyNoInteractions(fanout, acknowledgment);
  }
}
