package com.sportsbook.settlement.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.settlement.correction.ResultCandidate;
import com.sportsbook.settlement.correction.ResultCandidateStore;
import com.sportsbook.settlement.result.MatchOutcomeMode;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PostgresCandidateDecisionIntegrationTest extends PostgresIntegrationSupport {

  @Autowired private ResultCandidateStore candidates;

  @Test
  void executesAcceptanceReplacementApprovalAndRejection() {
    UUID eventId = UUID.randomUUID();
    UUID selectionId = UUID.randomUUID();
    Instant now = Instant.parse("2026-08-22T03:00:00Z");
    ResultCandidate first = candidate(eventId, selectionId, SettlementResult.WON, 'a', now, null);
    ResultCandidate replacement =
        candidate(
            eventId,
            selectionId,
            SettlementResult.LOST,
            'b',
            now.plusSeconds(1),
            first.candidateId());
    ResultCandidate approved =
        candidate(
            eventId,
            selectionId,
            SettlementResult.PUSH,
            'c',
            now.plusSeconds(2),
            replacement.candidateId());
    ResultCandidate rejected =
        candidate(
            eventId,
            selectionId,
            SettlementResult.VOID,
            'd',
            now.plusSeconds(3),
            approved.candidateId());

    assertThat(candidates.record(first).kind()).isEqualTo(ResultCandidateStore.RecordKind.CREATED);
    assertThat(candidates.acceptFirst(first.candidateId(), now)).isTrue();
    assertThat(candidates.record(replacement).kind())
        .isEqualTo(ResultCandidateStore.RecordKind.CREATED);
    assertThat(candidates.replaceAccepted(replacement.candidateId(), first.candidateId(), now))
        .isTrue();
    assertThat(candidates.record(approved).kind())
        .isEqualTo(ResultCandidateStore.RecordKind.CREATED);
    assertThat(candidates.approve(approved.candidateId(), now)).isTrue();
    assertThat(candidates.record(rejected).kind())
        .isEqualTo(ResultCandidateStore.RecordKind.CREATED);
    assertThat(candidates.reject(rejected.candidateId(), now, "operator rejected")).isTrue();

    assertThat(candidates.findAcceptedCandidateId(eventId)).contains(approved.candidateId());
    assertThat(state(first)).isEqualTo("SUPERSEDED");
    assertThat(state(replacement)).isEqualTo("SUPERSEDED");
    assertThat(state(approved)).isEqualTo("ACCEPTED");
    assertThat(state(rejected)).isEqualTo("REJECTED");
    assertThat(
            jdbc.queryForObject(
                "select outcome from match_selection_result where event_id = ?",
                String.class,
                eventId))
        .isEqualTo("PUSH");
  }

  private ResultCandidate candidate(
      UUID eventId,
      UUID selectionId,
      SettlementResult outcome,
      char fingerprint,
      Instant receivedAt,
      UUID replaces) {
    return ResultCandidate.pending(
        eventId,
        String.valueOf(fingerprint).repeat(64),
        MatchOutcomeMode.COMPLETED,
        Map.of(selectionId, outcome),
        receivedAt.minusSeconds(1),
        receivedAt,
        replaces);
  }

  private String state(ResultCandidate candidate) {
    return jdbc.queryForObject(
        "select state from result_candidate where candidate_id = ?",
        String.class,
        candidate.candidateId());
  }
}
