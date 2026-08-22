package com.sportsbook.settlement.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sportsbook.settlement.lifecycle.LifecycleFanout;
import com.sportsbook.settlement.lifecycle.LifecycleStore;
import com.sportsbook.settlement.readmodel.BetReadModelWriter;
import com.sportsbook.settlement.result.AcceptedResult;
import com.sportsbook.settlement.result.AcceptedResultRepository;
import com.sportsbook.settlement.result.MatchOutcomeMode;
import com.sportsbook.settlement.result.ResultFanout;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

class BetPlacedResultReplayTest {

  @Test
  void exactPlacementReplayRestartsAcceptedResultCatchupBeforeAck() {
    BetReadModelWriter writer = mock(BetReadModelWriter.class);
    LifecycleStore lifecycles = mock(LifecycleStore.class);
    LifecycleFanout lifecycleFanout = mock(LifecycleFanout.class);
    AcceptedResultRepository acceptedResults = mock(AcceptedResultRepository.class);
    ResultFanout resultFanout = mock(ResultFanout.class);
    Acknowledgment acknowledgment = mock(Acknowledgment.class);
    var event = BetPlacedListenerTest.event();
    UUID eventId = UUID.fromString(event.getSelections().get(0).getEventId().toString());
    AcceptedResult accepted =
        new AcceptedResult(
            eventId, UUID.randomUUID(), MatchOutcomeMode.VOIDED, Map.of(), Instant.EPOCH);
    when(writer.record(any())).thenReturn(BetReadModelWriter.RecordResult.EXACT_REPLAY);
    when(acceptedResults.findByEventId(eventId)).thenReturn(Optional.of(accepted));
    var listener =
        new BetPlacedListener(writer, lifecycles, lifecycleFanout, acceptedResults, resultFanout);

    listener.receive(
        BetPlacedListenerTest.record(event, event.getUserId().toString()), acknowledgment);

    var order = inOrder(writer, acceptedResults, resultFanout, acknowledgment);
    order.verify(writer).record(any());
    order.verify(acceptedResults).findByEventId(eventId);
    order.verify(resultFanout).fanOut(accepted);
    order.verify(acknowledgment).acknowledge();
  }
}
