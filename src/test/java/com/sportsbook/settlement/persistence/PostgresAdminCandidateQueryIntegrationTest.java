package com.sportsbook.settlement.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.settlement.admin.AdminCandidateQueryRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PostgresAdminCandidateQueryIntegrationTest extends PostgresIntegrationSupport {

  @Autowired private AdminCandidateQueryRepository candidates;

  @Test
  void exposesFutureHoldStateWithoutTheImmutableFingerprint() {
    PendingBet bet = insertPendingBet(UUID.randomUUID());
    Instant future = Instant.parse("2099-01-01T00:00:00Z");
    UUID candidateId =
        insertResultCandidate(
            bet.eventId(), bet.selectionId(), SettlementResult.WON, future, "PENDING");
    jdbc.update(
        "update result_candidate set decision_reason='FUTURE_HELD' where candidate_id=?",
        candidateId);

    AdminCandidateQueryRepository.View view = candidates.find(candidateId).orElseThrow();

    assertThat(view.candidateId()).isEqualTo(candidateId);
    assertThat(view.eventId()).isEqualTo(bet.eventId());
    assertThat(view.state()).isEqualTo("PENDING");
    assertThat(view.decisionReason()).isEqualTo("FUTURE_HELD");
    assertThat(view.settledAt()).isEqualTo(future);
    assertThat(view.accepted()).isFalse();
    assertThat(AdminCandidateQueryRepository.View.class.getRecordComponents())
        .extracting(component -> component.getName())
        .doesNotContain("fingerprint", "candidateSequence", "idempotencyKey");
  }
}
