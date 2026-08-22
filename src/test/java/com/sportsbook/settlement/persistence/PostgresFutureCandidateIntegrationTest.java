package com.sportsbook.settlement.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.settlement.correction.ResultCandidateIntake;
import com.sportsbook.settlement.correction.ResultCandidateStore;
import com.sportsbook.settlement.correction.RevisionWalletGateway;
import com.sportsbook.settlement.result.MatchOutcomeMode;
import com.sportsbook.settlement.result.MatchResultRecord;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

class PostgresFutureCandidateIntegrationTest extends PostgresIntegrationSupport {

  @Autowired private ResultCandidateIntake intake;
  @Autowired private ResultCandidateStore candidates;
  @MockBean private RevisionWalletGateway wallet;

  @Test
  void holdsFutureEvidenceAndFencesOperatorApproval() {
    UUID eventId = UUID.randomUUID();
    UUID selectionId = UUID.randomUUID();
    Instant receivedAt = Instant.now();
    MatchResultRecord first =
        new MatchResultRecord(
            eventId,
            MatchOutcomeMode.COMPLETED,
            Map.of(selectionId, SettlementResult.WON),
            receivedAt.minusSeconds(2),
            receivedAt.minusSeconds(1));
    assertThat(intake.ingest(first)).isEqualTo(ResultCandidateIntake.IntakeResult.FIRST_ACCEPTED);
    UUID accepted = candidates.findAcceptedCandidateId(eventId).orElseThrow();
    MatchResultRecord future =
        new MatchResultRecord(
            eventId,
            MatchOutcomeMode.COMPLETED,
            Map.of(selectionId, SettlementResult.LOST),
            receivedAt.plusSeconds(3600),
            receivedAt);

    assertThat(intake.ingest(future)).isEqualTo(ResultCandidateIntake.IntakeResult.FUTURE_HELD);

    UUID held =
        jdbc.queryForObject(
            "select candidate_id from result_candidate where decision_reason='FUTURE_HELD'",
            UUID.class);
    assertThat(
            jdbc.queryForObject(
                "select state from result_candidate where candidate_id=?", String.class, held))
        .isEqualTo("PENDING");
    assertThat(candidates.approve(held, receivedAt.plusSeconds(1))).isFalse();
    assertThat(candidates.findAcceptedCandidateId(eventId)).contains(accepted);
    assertThat(jdbc.queryForObject("select count(*) from settlement_revision", Integer.class))
        .isZero();
    assertThat(jdbc.queryForObject("select count(*) from outbox_event", Integer.class)).isZero();
    verifyNoInteractions(wallet);
  }
}
