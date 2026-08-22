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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PostgresConcurrentResultClaimIntegrationTest extends PostgresIntegrationSupport {

  @Autowired private ResultCandidateIntake intake;
  @Autowired private AcceptedResultRepository acceptedResults;
  @Autowired private ResultSettlementPreparer preparer;
  @Autowired private BetRepository bets;

  @Test
  void serializesTwoCompletingEventsIntoOneImmutableAttempt() throws Exception {
    UUID firstEvent = UUID.randomUUID();
    UUID secondEvent = UUID.randomUUID();
    UUID firstSelection = UUID.randomUUID();
    UUID secondSelection = UUID.randomUUID();
    var selections = new LinkedHashMap<UUID, UUID>();
    selections.put(firstEvent, firstSelection);
    selections.put(secondEvent, secondSelection);
    PendingMultiple bet = insertPendingMultiple(selections);
    accept(firstEvent, firstSelection);
    accept(secondEvent, secondSelection);
    AcceptedResult first = acceptedResults.findByEventId(firstEvent).orElseThrow();
    AcceptedResult second = acceptedResults.findByEventId(secondEvent).orElseThrow();
    CyclicBarrier start = new CyclicBarrier(2);
    var workers = Executors.newFixedThreadPool(2);

    try {
      var firstClaim = workers.submit(() -> prepare(start, bet.betId(), first));
      var secondClaim = workers.submit(() -> prepare(start, bet.betId(), second));

      assertThat(java.util.List.of(firstClaim.get(), secondClaim.get()))
          .filteredOn(java.util.Optional::isPresent)
          .hasSize(1);
    } finally {
      workers.shutdownNow();
      assertThat(workers.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }

    assertThat(
            jdbc.queryForMap(
                "select action,result,payout_amount,attempt_count from settlement_attempt "
                    + "where bet_id=?",
                bet.betId()))
        .containsEntry("action", "SETTLE")
        .containsEntry("result", "WON")
        .containsEntry("payout_amount", 400L)
        .containsEntry("attempt_count", 1);
    assertThat(bets.findWithSelectionsById(bet.betId()).orElseThrow().selections())
        .extracting(selection -> selection.sourceCandidateId())
        .containsExactly(first.candidateId(), second.candidateId());
  }

  private void accept(UUID eventId, UUID selectionId) {
    var result =
        new MatchResultRecord(
            eventId,
            MatchOutcomeMode.COMPLETED,
            Map.of(selectionId, SettlementResult.WON),
            Instant.EPOCH,
            Instant.now());
    assertThat(intake.ingest(result)).isEqualTo(ResultCandidateIntake.IntakeResult.FIRST_ACCEPTED);
  }

  private java.util.Optional<?> prepare(CyclicBarrier start, UUID betId, AcceptedResult accepted)
      throws Exception {
    start.await(5, TimeUnit.SECONDS);
    return preparer.prepare(betId, accepted);
  }
}
