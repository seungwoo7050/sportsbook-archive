package com.sportsbook.settlement.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.protocol.event.BetResolutionRevised;
import com.sportsbook.settlement.correction.CorrectionFanout;
import com.sportsbook.settlement.correction.RevisionWalletGateway;
import com.sportsbook.settlement.event.StrictAvroDecoder;
import com.sportsbook.settlement.result.AcceptedResultRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

class PostgresVoidedResultCorrectionIntegrationTest extends PostgresIntegrationSupport {

  @Autowired private AcceptedResultRepository acceptedResults;
  @Autowired private CorrectionFanout corrections;
  @MockBean private RevisionWalletGateway wallet;

  @Test
  void revisesMarketVoidWithoutCreatingAWholeSlipVoid() {
    PendingBet bet = insertPendingBet(UUID.randomUUID());
    Instant sourceTime = Instant.parse("2026-08-22T00:00:00Z");
    UUID previous =
        insertResultCandidate(
            bet.eventId(), bet.selectionId(), SettlementResult.LOST, sourceTime, "SUPERSEDED");
    UUID accepted =
        insertResultCandidate(
            bet.eventId(),
            bet.selectionId(),
            SettlementResult.LOST,
            sourceTime.plusSeconds(1),
            "ACCEPTED");
    settleBet(bet, previous, SettlementResult.LOST, 0);
    acceptResult(bet, accepted, SettlementResult.LOST, sourceTime.plusSeconds(1));
    jdbc.update("update match_result set mode='VOIDED' where event_id=?", bet.eventId());
    jdbc.update("delete from match_selection_result where event_id=?", bet.eventId());
    when(wallet.submit(any())).thenAnswer(call -> CorrectionProofs.applied(call.getArgument(0)));

    assertThat(corrections.fanOut(acceptedResults.findByEventId(bet.eventId()).orElseThrow()))
        .hasSize(1);

    assertThat(
            jdbc.queryForMap(
                "select status,result,payout_amount from bet where bet_id=?", bet.betId()))
        .containsEntry("status", "SETTLED")
        .containsEntry("result", "VOID")
        .containsEntry("payout_amount", 100L);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from outbox_event where schema_name='BetVoided'", Integer.class))
        .isZero();
    byte[] payload =
        jdbc.queryForObject(
            "select payload from outbox_event where schema_name='BetResolutionRevised'",
            byte[].class);
    BetResolutionRevised event =
        new StrictAvroDecoder().decode(payload, BetResolutionRevised.class);
    assertThat(event.getNewResult().name()).isEqualTo("VOID");
  }
}
