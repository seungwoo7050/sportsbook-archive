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

class PostgresPayoutCorrectionIntegrationTest extends PostgresIntegrationSupport {

  @Autowired private AcceptedResultRepository acceptedResults;
  @Autowired private CorrectionFanout corrections;
  @MockBean private RevisionWalletGateway wallet;

  @Test
  void appliesPayoutIncreaseAndDecreaseFromOneAcceptedSnapshot() {
    UUID eventId = UUID.randomUUID();
    PendingBet increase = insertPendingBet(eventId);
    PendingBet decrease = insertPendingBet(eventId);
    Instant sourceTime = Instant.parse("2026-08-22T00:00:00Z");
    UUID previous =
        insertResultCandidate(
            eventId, increase.selectionId(), SettlementResult.LOST, sourceTime, "SUPERSEDED");
    UUID accepted =
        insertResultCandidate(
            eventId,
            increase.selectionId(),
            SettlementResult.WON,
            sourceTime.plusSeconds(1),
            "ACCEPTED");
    jdbc.update(
        "insert into result_candidate_selection (candidate_id,selection_id,outcome) "
            + "values (?,?,'LOST')",
        accepted,
        decrease.selectionId());
    settleBet(increase, previous, SettlementResult.LOST, 0);
    settleBet(decrease, previous, SettlementResult.WON, 200);
    acceptResult(increase, accepted, SettlementResult.WON, sourceTime.plusSeconds(1));
    jdbc.update(
        "insert into match_selection_result (event_id,selection_id,outcome) "
            + "values (?,?,'LOST')",
        eventId,
        decrease.selectionId());
    when(wallet.submit(any())).thenAnswer(call -> CorrectionProofs.applied(call.getArgument(0)));

    assertThat(corrections.fanOut(acceptedResults.findByEventId(eventId).orElseThrow())).hasSize(2);

    ArgumentCaptor<RevisionPlan> plans = ArgumentCaptor.forClass(RevisionPlan.class);
    verify(wallet, times(2)).submit(plans.capture());
    assertThat(plans.getAllValues())
        .extracting(RevisionPlan::deltaAmount)
        .containsExactlyInAnyOrder(200L, -200L);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from settlement_revision where state='APPLIED'", Integer.class))
        .isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from outbox_event where schema_name='BetResolutionRevised'",
                Integer.class))
        .isEqualTo(2);
  }
}
