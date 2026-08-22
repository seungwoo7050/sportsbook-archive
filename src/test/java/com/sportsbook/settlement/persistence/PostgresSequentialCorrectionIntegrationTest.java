package com.sportsbook.settlement.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.settlement.correction.CorrectionFanout;
import com.sportsbook.settlement.correction.RevisionPlan;
import com.sportsbook.settlement.correction.RevisionWalletGateway;
import com.sportsbook.settlement.result.AcceptedResultRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

class PostgresSequentialCorrectionIntegrationTest extends PostgresIntegrationSupport {

  @Autowired private AcceptedResultRepository acceptedResults;
  @Autowired private CorrectionFanout corrections;
  @MockBean private RevisionWalletGateway wallet;

  @Test
  void appliesTwoAcceptedCorrectionsAsStrictlyIncreasingSnapshots() {
    PendingBet bet = insertPendingBet(UUID.randomUUID());
    Instant sourceTime = Instant.parse("2026-08-22T00:00:00Z");
    UUID base =
        insertResultCandidate(
            bet.eventId(), bet.selectionId(), SettlementResult.LOST, sourceTime, "SUPERSEDED");
    UUID first =
        insertResultCandidate(
            bet.eventId(),
            bet.selectionId(),
            SettlementResult.WON,
            sourceTime.plusSeconds(1),
            "ACCEPTED");
    settleBet(bet, base, SettlementResult.LOST, 0);
    acceptResult(bet, first, SettlementResult.WON, sourceTime.plusSeconds(1));
    when(wallet.submit(any())).thenAnswer(call -> CorrectionProofs.applied(call.getArgument(0)));

    assertThat(corrections.fanOut(acceptedResults.findByEventId(bet.eventId()).orElseThrow()))
        .hasSize(1);
    UUID second =
        insertResultCandidate(
            bet.eventId(),
            bet.selectionId(),
            SettlementResult.LOST,
            sourceTime.plusSeconds(2),
            "PENDING");
    replaceAcceptedResult(bet, second, SettlementResult.LOST, sourceTime.plusSeconds(2));
    assertThat(corrections.fanOut(acceptedResults.findByEventId(bet.eventId()).orElseThrow()))
        .hasSize(1);
    assertThat(corrections.fanOut(acceptedResults.findByEventId(bet.eventId()).orElseThrow()))
        .isEmpty();

    ArgumentCaptor<RevisionPlan> plans = ArgumentCaptor.forClass(RevisionPlan.class);
    verify(wallet, times(2)).submit(plans.capture());
    assertThat(plans.getAllValues())
        .extracting(plan -> plan.target().revisionNumber())
        .containsExactly(1L, 2L);
    assertThat(plans.getAllValues())
        .extracting(
            plan -> plan.target().previousPayout().amount() + "->" + plan.newPayout().amount())
        .containsExactly("0->200", "200->0");
    assertThat(
            jdbc.queryForObject(
                "select revision_number from bet where bet_id=?", Long.class, bet.betId()))
        .isEqualTo(2L);
    assertThat(
            jdbc.queryForObject(
                "select source_candidate_id from bet_selection where bet_id=?",
                UUID.class,
                bet.betId()))
        .isEqualTo(second);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from outbox_event where schema_name='BetResolutionRevised'",
                Integer.class))
        .isEqualTo(2);
  }
}
