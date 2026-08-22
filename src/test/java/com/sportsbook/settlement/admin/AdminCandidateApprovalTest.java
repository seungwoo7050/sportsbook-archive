package com.sportsbook.settlement.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sportsbook.settlement.correction.ResultCandidateState;
import com.sportsbook.settlement.correction.ResultCandidateStore;
import com.sportsbook.settlement.persistence.DatabaseTimeSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class AdminCandidateApprovalTest {

  private final AdminActionRepository actions = mock(AdminActionRepository.class);
  private final ResultCandidateStore candidates = mock(ResultCandidateStore.class);
  private final JdbcTemplate timeJdbc = mock(JdbcTemplate.class);
  private final DatabaseTimeSource databaseTime = new DatabaseTimeSource(timeJdbc);
  private final AdminCandidateApproval approval =
      new AdminCandidateApproval(actions, candidates, databaseTime);

  @Test
  void atomicallyApprovesTheFirstDueCandidate() {
    UUID key = UUID.randomUUID();
    UUID candidateId = UUID.randomUUID();
    UUID eventId = UUID.randomUUID();
    Instant now = Instant.parse("2026-08-22T00:00:00Z");
    var candidate =
        new ResultCandidateStore.AdminCandidate(
            eventId, ResultCandidateState.PENDING, now, null, null);
    AdminAction action = action(key, candidateId);
    when(actions.lockAndFind(key)).thenReturn(Optional.empty());
    when(candidates.lockForAdmin(candidateId)).thenReturn(Optional.of(candidate));
    when(timeJdbc.queryForObject("select current_timestamp", Timestamp.class))
        .thenReturn(Timestamp.from(now));
    when(candidates.approveFirst(candidateId, now)).thenReturn(true);
    when(actions.append(
            key,
            AdminAction.Kind.CANDIDATE_APPROVE,
            candidateId,
            action.requestFingerprint(),
            AdminAction.Outcome.CANDIDATE_APPROVED,
            null))
        .thenReturn(action);

    assertThat(approval.decide(key, candidateId))
        .isEqualTo(new AdminCandidateApproval.Decision(action, eventId, false));
  }

  @Test
  void rejectsFutureCandidatesBeforeChangingTheSnapshot() {
    UUID key = UUID.randomUUID();
    UUID candidateId = UUID.randomUUID();
    Instant now = Instant.parse("2026-08-22T00:00:00Z");
    when(actions.lockAndFind(key)).thenReturn(Optional.empty());
    when(candidates.lockForAdmin(candidateId))
        .thenReturn(
            Optional.of(
                new ResultCandidateStore.AdminCandidate(
                    UUID.randomUUID(),
                    ResultCandidateState.PENDING,
                    now.plusSeconds(1),
                    null,
                    null)));
    when(timeJdbc.queryForObject("select current_timestamp", Timestamp.class))
        .thenReturn(Timestamp.from(now));

    assertThatThrownBy(() -> approval.decide(key, candidateId))
        .isInstanceOf(AdminControlException.class)
        .hasMessage("Result candidate is not due");
    verify(candidates, never()).approveFirst(candidateId, now);
  }

  private static AdminAction action(UUID key, UUID candidateId) {
    return new AdminAction(
        key,
        AdminAction.Kind.CANDIDATE_APPROVE,
        candidateId,
        AdminRequestFingerprint.create(AdminAction.Kind.CANDIDATE_APPROVE, candidateId, ""),
        AdminAction.Outcome.CANDIDATE_APPROVED,
        null,
        Instant.EPOCH,
        Instant.EPOCH);
  }
}
