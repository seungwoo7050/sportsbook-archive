package com.sportsbook.settlement.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
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

class AdminCandidateRejectionTest {

  @Test
  void rejectsPendingFutureHoldsWithTheNormalizedReason() {
    AdminActionRepository actions = mock(AdminActionRepository.class);
    ResultCandidateStore candidates = mock(ResultCandidateStore.class);
    JdbcTemplate timeJdbc = mock(JdbcTemplate.class);
    UUID key = UUID.randomUUID();
    UUID candidateId = UUID.randomUUID();
    Instant now = Instant.parse("2026-08-22T00:00:00Z");
    String fingerprint =
        AdminRequestFingerprint.create(
            AdminAction.Kind.CANDIDATE_REJECT, candidateId, "BAD RESULT");
    AdminAction action = action(key, candidateId, fingerprint);
    when(actions.lockAndFind(key)).thenReturn(Optional.empty());
    when(candidates.lockForAdmin(candidateId))
        .thenReturn(
            Optional.of(
                new ResultCandidateStore.AdminCandidate(
                    UUID.randomUUID(),
                    ResultCandidateState.PENDING,
                    now.plusSeconds(3600),
                    null,
                    null)));
    when(timeJdbc.queryForObject("select current_timestamp", Timestamp.class))
        .thenReturn(Timestamp.from(now));
    when(candidates.reject(candidateId, now, "BAD RESULT")).thenReturn(true);
    when(actions.append(
            key,
            AdminAction.Kind.CANDIDATE_REJECT,
            candidateId,
            fingerprint,
            AdminAction.Outcome.CANDIDATE_REJECTED,
            null))
        .thenReturn(action);
    var rejection =
        new AdminCandidateRejection(actions, candidates, new DatabaseTimeSource(timeJdbc));

    assertThat(rejection.decide(key, candidateId, "  BAD RESULT  "))
        .isEqualTo(new AdminCandidateRejection.Decision(action, false));
  }

  @Test
  void rejectsBlankOrControlCharacterReasonsBeforePersistence() {
    AdminActionRepository actions = mock(AdminActionRepository.class);
    var rejection =
        new AdminCandidateRejection(
            actions,
            mock(ResultCandidateStore.class),
            new DatabaseTimeSource(mock(JdbcTemplate.class)));

    assertThatThrownBy(() -> rejection.decide(UUID.randomUUID(), UUID.randomUUID(), "bad\nreason"))
        .isInstanceOf(AdminControlException.class)
        .hasMessage("Rejection reason must be 1 to 256 printable characters");
    verifyNoInteractions(actions);
  }

  private static AdminAction action(UUID key, UUID target, String fingerprint) {
    return new AdminAction(
        key,
        AdminAction.Kind.CANDIDATE_REJECT,
        target,
        fingerprint,
        AdminAction.Outcome.CANDIDATE_REJECTED,
        null,
        Instant.EPOCH,
        Instant.EPOCH);
  }
}
