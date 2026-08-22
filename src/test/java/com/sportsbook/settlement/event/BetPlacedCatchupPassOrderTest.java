package com.sportsbook.settlement.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sportsbook.protocol.event.BetPlacedRequested;
import com.sportsbook.protocol.event.BetSlipTypeTag;
import com.sportsbook.protocol.event.EventLifecycleStatus;
import com.sportsbook.protocol.event.RequestedSelection;
import com.sportsbook.settlement.lifecycle.LifecycleFanout;
import com.sportsbook.settlement.lifecycle.LifecycleObservation;
import com.sportsbook.settlement.lifecycle.LifecycleStore;
import com.sportsbook.settlement.readmodel.BetReadModelWriter;
import com.sportsbook.settlement.result.AcceptedResult;
import com.sportsbook.settlement.result.AcceptedResultRepository;
import com.sportsbook.settlement.result.MatchOutcomeMode;
import com.sportsbook.settlement.result.ResultFanout;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

class BetPlacedCatchupPassOrderTest {

  @Test
  void completesEveryLifecyclePassBeforeStartingResultCatchup() {
    UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
    UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");
    var event =
        BetPlacedRequested.newBuilder(BetPlacedListenerTest.event())
            .setSlipType(BetSlipTypeTag.MULTIPLE)
            .setSelections(List.of(selection(second), selection(first)))
            .build();
    BetReadModelWriter writer = mock(BetReadModelWriter.class);
    LifecycleStore lifecycles = mock(LifecycleStore.class);
    LifecycleFanout lifecycleFanout = mock(LifecycleFanout.class);
    AcceptedResultRepository acceptedResults = mock(AcceptedResultRepository.class);
    ResultFanout resultFanout = mock(ResultFanout.class);
    Acknowledgment acknowledgment = mock(Acknowledgment.class);
    LifecycleObservation firstTombstone = tombstone(first);
    LifecycleObservation secondTombstone = tombstone(second);
    AcceptedResult firstResult = accepted(first);
    AcceptedResult secondResult = accepted(second);
    when(lifecycles.findTombstone(first)).thenReturn(Optional.of(firstTombstone));
    when(lifecycles.findTombstone(second)).thenReturn(Optional.of(secondTombstone));
    when(acceptedResults.findByEventId(first)).thenReturn(Optional.of(firstResult));
    when(acceptedResults.findByEventId(second)).thenReturn(Optional.of(secondResult));
    var listener =
        new BetPlacedListener(writer, lifecycles, lifecycleFanout, acceptedResults, resultFanout);

    listener.receive(
        BetPlacedListenerTest.record(event, event.getUserId().toString()), acknowledgment);

    var order =
        inOrder(writer, lifecycles, lifecycleFanout, acceptedResults, resultFanout, acknowledgment);
    order.verify(writer).record(any());
    order.verify(lifecycles).findTombstone(first);
    order.verify(lifecycleFanout).fanOut(firstTombstone);
    order.verify(lifecycles).findTombstone(second);
    order.verify(lifecycleFanout).fanOut(secondTombstone);
    order.verify(acceptedResults).findByEventId(first);
    order.verify(resultFanout).fanOut(firstResult);
    order.verify(acceptedResults).findByEventId(second);
    order.verify(resultFanout).fanOut(secondResult);
    order.verify(acknowledgment).acknowledge();
  }

  private static RequestedSelection selection(UUID eventId) {
    return RequestedSelection.newBuilder()
        .setEventId(eventId.toString())
        .setMarketId(UUID.randomUUID().toString())
        .setSelectionId(UUID.randomUUID().toString())
        .setOddsAtSubmission("2.0000")
        .build();
  }

  private static LifecycleObservation tombstone(UUID eventId) {
    return LifecycleObservation.observe(
        eventId, EventLifecycleStatus.CANCELLED, Instant.EPOCH, null, Instant.EPOCH);
  }

  private static AcceptedResult accepted(UUID eventId) {
    return new AcceptedResult(
        eventId, UUID.randomUUID(), MatchOutcomeMode.VOIDED, Map.of(), Instant.EPOCH);
  }
}
