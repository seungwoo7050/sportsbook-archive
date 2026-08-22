package com.sportsbook.settlement.correction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.settlement.config.SettlementRuntimeProperties;
import com.sportsbook.settlement.persistence.BetRepository;
import com.sportsbook.settlement.persistence.DatabaseTimeSource;
import com.sportsbook.settlement.result.AcceptedResult;
import com.sportsbook.settlement.result.AcceptedResultRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class CorrectionPreparationFenceTest {

  private final BetRepository bets = mock(BetRepository.class);
  private final AcceptedResultRepository acceptedResults = mock(AcceptedResultRepository.class);
  private final RevisionPlanRepository revisions = mock(RevisionPlanRepository.class);
  private final DatabaseTimeSource databaseTime = new DatabaseTimeSource(mock(JdbcTemplate.class));

  @Test
  void skipsAResultThatLostAuthoritativeAcceptanceAfterTheBetLock() {
    var fixture = CorrectionFixtures.settledSingle(SettlementResult.LOST);
    AcceptedResult newer =
        new AcceptedResult(
            fixture.accepted().eventId(),
            UUID.randomUUID(),
            fixture.accepted().mode(),
            fixture.accepted().outcomes(),
            fixture.accepted().sourceSettledAt());
    when(bets.findForUpdateById(fixture.bet().betId())).thenReturn(Optional.of(fixture.bet()));
    when(acceptedResults.findByEventId(fixture.accepted().eventId()))
        .thenReturn(Optional.of(newer));

    assertThat(preparer().prepare(fixture.bet().betId(), fixture.accepted())).isEmpty();

    verifyNoInteractions(revisions);
  }

  @Test
  void skipsABetWhoseNextRevisionIsAlreadyOwned() {
    var fixture = CorrectionFixtures.settledSingle(SettlementResult.LOST);
    when(bets.findForUpdateById(fixture.bet().betId())).thenReturn(Optional.of(fixture.bet()));
    when(acceptedResults.findByEventId(fixture.accepted().eventId()))
        .thenReturn(Optional.of(fixture.accepted()));
    when(revisions.exists(fixture.bet().betId(), 1)).thenReturn(true);

    assertThat(preparer().prepare(fixture.bet().betId(), fixture.accepted())).isEmpty();

    verify(revisions, never()).persist(any(), any());
  }

  private CorrectionRevisionPreparer preparer() {
    return new CorrectionRevisionPreparer(
        bets,
        acceptedResults,
        revisions,
        new SettlementRuntimeProperties(null, null, null, 0),
        databaseTime);
  }
}
