package com.sportsbook.settlement.correction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sportsbook.settlement.result.AcceptedResult;
import com.sportsbook.settlement.result.AcceptedResultRepository;
import com.sportsbook.settlement.result.MatchOutcomeMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CorrectionCatchupScannerTest {

  private final CorrectionTargetRepository targets = mock(CorrectionTargetRepository.class);
  private final AcceptedResultRepository acceptedResults = mock(AcceptedResultRepository.class);
  private final CorrectionFanout fanout = mock(CorrectionFanout.class);
  private final CorrectionCatchupScanner scanner =
      new CorrectionCatchupScanner(targets, acceptedResults, fanout);

  @Test
  void fansOutOneAuthoritativeAcceptedEventPerTick() {
    UUID eventId = UUID.randomUUID();
    AcceptedResult accepted =
        new AcceptedResult(
            eventId, UUID.randomUUID(), MatchOutcomeMode.VOIDED, Map.of(), Instant.EPOCH);
    when(targets.findNextActionableEvent()).thenReturn(Optional.of(eventId));
    when(acceptedResults.findByEventId(eventId)).thenReturn(Optional.of(accepted));
    when(fanout.fanOut(accepted)).thenReturn(List.of(RevisionExecutionRunner.Result.APPLIED));

    assertThat(scanner.catchUp()).containsExactly(RevisionExecutionRunner.Result.APPLIED);

    var order = inOrder(targets, acceptedResults, fanout);
    order.verify(targets).findNextActionableEvent();
    order.verify(acceptedResults).findByEventId(eventId);
    order.verify(fanout).fanOut(accepted);
  }

  @Test
  void performsNoWorkWithoutAStaleAcceptedEvent() {
    when(targets.findNextActionableEvent()).thenReturn(Optional.empty());

    assertThat(scanner.catchUp()).isEmpty();
    verifyNoInteractions(acceptedResults, fanout);
  }
}
