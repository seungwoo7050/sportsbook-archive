package com.sportsbook.settlement.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.settlement.correction.CorrectionCatchupScanner;
import com.sportsbook.settlement.correction.CorrectionFanout;
import com.sportsbook.settlement.correction.CorrectionTargetRepository;
import com.sportsbook.settlement.correction.RevisionExecutionRunner;
import com.sportsbook.settlement.correction.RevisionWalletGateway;
import com.sportsbook.settlement.result.AcceptedResultRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

class PostgresCorrectionAfterBaseCatchupIntegrationTest extends PostgresIntegrationSupport {

  @Autowired private CorrectionTargetRepository targets;
  @Autowired private AcceptedResultRepository acceptedResults;
  @Autowired private CorrectionFanout corrections;
  @MockBean private RevisionWalletGateway wallet;

  @Test
  void catchesUpACorrectionThatArrivedBeforeBaseSettlementCompleted() {
    PendingBet bet = insertPendingBet(UUID.randomUUID());
    Instant sourceTime = Instant.parse("2026-08-22T00:00:00Z");
    UUID previous =
        insertResultCandidate(
            bet.eventId(), bet.selectionId(), SettlementResult.LOST, sourceTime, "SUPERSEDED");
    UUID accepted =
        insertResultCandidate(
            bet.eventId(),
            bet.selectionId(),
            SettlementResult.WON,
            sourceTime.plusSeconds(1),
            "ACCEPTED");
    acceptResult(bet, accepted, SettlementResult.WON, sourceTime.plusSeconds(1));
    var snapshot = acceptedResults.findByEventId(bet.eventId()).orElseThrow();

    assertThat(corrections.fanOut(snapshot)).isEmpty();
    assertThat(jdbc.queryForObject("select count(*) from settlement_revision", Integer.class))
        .isZero();

    settleBet(bet, previous, SettlementResult.LOST, 0);
    when(wallet.submit(any())).thenAnswer(call -> CorrectionProofs.applied(call.getArgument(0)));
    var results = new CorrectionCatchupScanner(targets, acceptedResults, corrections).catchUp();

    assertThat(results).containsExactly(RevisionExecutionRunner.Result.APPLIED);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from settlement_revision where state='APPLIED'", Integer.class))
        .isOne();
    assertThat(
            jdbc.queryForObject(
                "select source_candidate_id from bet_selection where bet_id=?",
                UUID.class,
                bet.betId()))
        .isEqualTo(accepted);
  }
}
