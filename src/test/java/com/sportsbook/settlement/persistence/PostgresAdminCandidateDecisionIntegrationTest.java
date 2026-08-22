package com.sportsbook.settlement.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.settlement.admin.AdminCandidateApproval;
import com.sportsbook.settlement.admin.AdminCandidateRejection;
import com.sportsbook.settlement.admin.AdminControlException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PostgresAdminCandidateDecisionIntegrationTest extends PostgresIntegrationSupport {

  @Autowired private AdminCandidateApproval approvals;
  @Autowired private AdminCandidateRejection rejections;

  @BeforeEach
  void clearAdminActions() {
    jdbc.execute("truncate table settlement_admin_action");
  }

  @Test
  void atomicallyAcceptsAndReplaysAFirstDueCandidate() {
    PendingBet bet = insertPendingBet(UUID.randomUUID());
    UUID candidateId =
        insertResultCandidate(
            bet.eventId(), bet.selectionId(), SettlementResult.WON, Instant.EPOCH, "PENDING");
    UUID key = UUID.randomUUID();

    assertThat(approvals.decide(key, candidateId).replay()).isFalse();
    assertThat(approvals.decide(key, candidateId).replay()).isTrue();

    assertThat(
            jdbc.queryForObject(
                "select c.state='ACCEPTED' and c.decision_reason='OPERATOR_APPROVED' "
                    + "and m.accepted_candidate_id=c.candidate_id from result_candidate c "
                    + "join match_result m on m.event_id=c.event_id where c.candidate_id=?",
                Boolean.class,
                candidateId))
        .isTrue();
    assertThat(jdbc.queryForObject("select count(*) from settlement_admin_action", Integer.class))
        .isOne();
  }

  @Test
  void rollsBackAFutureApprovalBeforeAllowingExplicitRejection() {
    PendingBet bet = insertPendingBet(UUID.randomUUID());
    UUID candidateId =
        insertResultCandidate(
            bet.eventId(),
            bet.selectionId(),
            SettlementResult.WON,
            Instant.parse("2099-01-01T00:00:00Z"),
            "PENDING");
    UUID key = UUID.randomUUID();

    assertThatThrownBy(() -> approvals.decide(key, candidateId))
        .isInstanceOf(AdminControlException.class)
        .hasMessage("Result candidate is not due");
    assertThat(rejections.decide(key, candidateId, "invalid future evidence").replay()).isFalse();

    assertThat(
            jdbc.queryForObject(
                "select state='REJECTED' and decision_reason='invalid future evidence' "
                    + "from result_candidate where candidate_id=?",
                Boolean.class,
                candidateId))
        .isTrue();
    assertThat(jdbc.queryForObject("select count(*) from settlement_admin_action", Integer.class))
        .isOne();
  }
}
