package com.sportsbook.settlement.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.settlement.correction.ResultCandidateIntake;
import com.sportsbook.settlement.result.AcceptedResult;
import com.sportsbook.settlement.result.AcceptedResultRepository;
import com.sportsbook.settlement.result.MatchOutcomeMode;
import com.sportsbook.settlement.result.MatchResultRecord;
import com.sportsbook.settlement.result.ResultSettlementPreparer;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PostgresOwnedAttemptProjectionIntegrationTest extends PostgresIntegrationSupport {

  @Autowired private ResultCandidateIntake intake;
  @Autowired private AcceptedResultRepository acceptedResults;
  @Autowired private ResultSettlementPreparer preparer;
  @Autowired private BetRepository bets;

  @Test
  void neverMixesANewCandidateSourceIntoAnExistingAttemptPlan() {
    PendingBet bet = insertPendingBet(UUID.randomUUID());
    var firstEvidence =
        new MatchResultRecord(
            bet.eventId(),
            MatchOutcomeMode.COMPLETED,
            Map.of(bet.selectionId(), SettlementResult.WON),
            Instant.EPOCH,
            Instant.now());
    assertThat(intake.ingest(firstEvidence))
        .isEqualTo(ResultCandidateIntake.IntakeResult.FIRST_ACCEPTED);
    AcceptedResult first = acceptedResults.findByEventId(bet.eventId()).orElseThrow();
    assertThat(preparer.prepare(bet.betId(), first)).isPresent();
    AcceptedResult replacement =
        new AcceptedResult(
            bet.eventId(),
            UUID.randomUUID(),
            MatchOutcomeMode.COMPLETED,
            Map.of(bet.selectionId(), SettlementResult.LOST),
            Instant.EPOCH.plusSeconds(1));

    assertThat(preparer.prepare(bet.betId(), replacement)).isEmpty();

    assertThat(bets.findWithSelectionsById(bet.betId()).orElseThrow().selections().get(0))
        .satisfies(
            selection -> {
              assertThat(selection.outcome()).isEqualTo(SettlementResult.WON);
              assertThat(selection.sourceCandidateId()).isEqualTo(first.candidateId());
            });
    assertThat(
            jdbc.queryForMap(
                "select result,payout_amount,attempt_count from settlement_attempt where bet_id=?",
                bet.betId()))
        .containsEntry("result", "WON")
        .containsEntry("payout_amount", 200L)
        .containsEntry("attempt_count", 1);
  }
}
