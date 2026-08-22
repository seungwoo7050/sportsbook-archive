package com.sportsbook.settlement.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.protocol.event.BetResolutionRevised;
import com.sportsbook.settlement.correction.CorrectionFanout;
import com.sportsbook.settlement.correction.RevisionExecutionRunner;
import com.sportsbook.settlement.correction.RevisionWalletGateway;
import com.sportsbook.settlement.event.StrictAvroDecoder;
import com.sportsbook.settlement.result.AcceptedResultRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

class PostgresZeroDeltaCorrectionIntegrationTest extends PostgresIntegrationSupport {

  @Autowired private AcceptedResultRepository acceptedResults;
  @Autowired private CorrectionFanout corrections;
  @MockBean private RevisionWalletGateway wallet;

  @Test
  void finalizesSourceOnlyCorrectionWithoutWalletUsingOneDatabaseTimestamp() {
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

    assertThat(corrections.fanOut(acceptedResults.findByEventId(bet.eventId()).orElseThrow()))
        .containsExactly(RevisionExecutionRunner.Result.APPLIED);

    verifyNoInteractions(wallet);
    var revision =
        jdbc.queryForMap(
            "select state,applied_at,source_result_settled_at from settlement_revision "
                + "where bet_id=?",
            bet.betId());
    Instant appliedAt = ((Timestamp) revision.get("applied_at")).toInstant();
    assertThat(revision.get("state")).isEqualTo("APPLIED");
    assertThat(((Timestamp) revision.get("source_result_settled_at")).toInstant())
        .isBeforeOrEqualTo(appliedAt);
    assertThat(
            jdbc.queryForObject(
                    "select settled_at from bet where bet_id=?", Timestamp.class, bet.betId())
                .toInstant())
        .isEqualTo(appliedAt);
    assertThat(
            jdbc.queryForObject(
                "select source_candidate_id from bet_selection where bet_id=?",
                UUID.class,
                bet.betId()))
        .isEqualTo(accepted);
    assertThat(
            jdbc.queryForObject(
                    "select created_at from outbox_event where schema_name='BetResolutionRevised'",
                    Timestamp.class)
                .toInstant())
        .isEqualTo(appliedAt);
    byte[] payload =
        jdbc.queryForObject(
            "select payload from outbox_event where schema_name='BetResolutionRevised'",
            byte[].class);
    BetResolutionRevised event =
        new StrictAvroDecoder().decode(payload, BetResolutionRevised.class);
    assertThat(event.getRevisedAt()).isEqualTo(appliedAt);
    assertThat(event.getSourceResultSettledAt()).isBeforeOrEqualTo(event.getRevisedAt());
  }
}
