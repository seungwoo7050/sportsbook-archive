package com.sportsbook.settlement.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sportsbook.settlement.correction.ResultCandidateState;
import com.sportsbook.settlement.correction.ResultCandidateStore;
import com.sportsbook.settlement.persistence.DatabaseTimeSource;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class AdminCandidateApprovalReplayTest {

  @Test
  void returnsTheOriginalActionWithoutRepeatingTheDecision() {
    AdminActionRepository actions = mock(AdminActionRepository.class);
    ResultCandidateStore candidates = mock(ResultCandidateStore.class);
    UUID key = UUID.randomUUID();
    UUID candidateId = UUID.randomUUID();
    UUID eventId = UUID.randomUUID();
    String fingerprint =
        AdminRequestFingerprint.create(AdminAction.Kind.CANDIDATE_APPROVE, candidateId, "");
    AdminAction action =
        new AdminAction(
            key,
            AdminAction.Kind.CANDIDATE_APPROVE,
            candidateId,
            fingerprint,
            AdminAction.Outcome.CANDIDATE_APPROVED,
            null,
            Instant.EPOCH,
            Instant.EPOCH);
    when(actions.lockAndFind(key)).thenReturn(Optional.of(action));
    when(candidates.lockForAdmin(candidateId))
        .thenReturn(
            Optional.of(
                new ResultCandidateStore.AdminCandidate(
                    eventId, ResultCandidateState.ACCEPTED, Instant.EPOCH, null, candidateId)));
    var approval =
        new AdminCandidateApproval(
            actions, candidates, new DatabaseTimeSource(mock(JdbcTemplate.class)));

    assertThat(approval.decide(key, candidateId))
        .isEqualTo(new AdminCandidateApproval.Decision(action, eventId, true));
    verify(candidates, never()).approveFirst(candidateId, Instant.EPOCH);
    verify(actions, never())
        .append(
            key,
            AdminAction.Kind.CANDIDATE_APPROVE,
            candidateId,
            fingerprint,
            AdminAction.Outcome.CANDIDATE_APPROVED,
            null);
  }
}
