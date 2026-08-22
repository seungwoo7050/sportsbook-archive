package com.sportsbook.settlement.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.settlement.correction.ResultCandidate;
import com.sportsbook.settlement.correction.ResultCandidateStore;
import com.sportsbook.settlement.result.MatchOutcomeMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class PostgresReplacementRollbackIntegrationTest extends PostgresIntegrationSupport {

  @Autowired private ResultCandidateStore candidates;
  @Autowired private PlatformTransactionManager transactionManager;

  @Test
  void rollsBackReplacementWhenEitherCandidateTransitionIsLost() {
    UUID eventId = UUID.randomUUID();
    UUID selectionId = UUID.randomUUID();
    ResultCandidate first = candidate(eventId, selectionId, 'a', null);
    candidates.record(first);
    candidates.acceptFirst(first.candidateId(), Instant.EPOCH);
    ResultCandidate replacement = candidate(eventId, selectionId, 'b', first.candidateId());
    candidates.record(replacement);

    for (UUID blocked : List.of(first.candidateId(), replacement.candidateId())) {
      assertBlockedUpdateRollsBack(blocked, first, replacement);
      assertThat(candidates.findAcceptedCandidateId(eventId)).contains(first.candidateId());
      assertThat(state(first)).isEqualTo("ACCEPTED");
      assertThat(state(replacement)).isEqualTo("PENDING");
    }
  }

  private void assertBlockedUpdateRollsBack(
      UUID blocked, ResultCandidate first, ResultCandidate replacement) {
    TransactionTemplate transactions = new TransactionTemplate(transactionManager);
    assertThatThrownBy(
            () ->
                transactions.executeWithoutResult(
                    ignored -> {
                      blockUpdate(blocked);
                      candidates.replaceAccepted(
                          replacement.candidateId(), first.candidateId(), Instant.EPOCH);
                    }))
        .hasRootCauseInstanceOf(IllegalStateException.class);
  }

  private void blockUpdate(UUID candidateId) {
    jdbc.execute(
        """
        create function block_candidate_update() returns trigger language plpgsql as $$
        begin if new.candidate_id::text = current_setting('test.block_candidate', true)
        then return null; end if; return new; end $$
        """);
    jdbc.execute(
        "create trigger block_candidate before update on result_candidate "
            + "for each row execute function block_candidate_update()");
    jdbc.queryForObject(
        "select set_config('test.block_candidate', ?, true)", String.class, candidateId.toString());
  }

  private ResultCandidate candidate(UUID event, UUID selection, char mark, UUID replaces) {
    return ResultCandidate.pending(
        event,
        String.valueOf(mark).repeat(64),
        MatchOutcomeMode.COMPLETED,
        Map.of(selection, SettlementResult.WON),
        Instant.EPOCH,
        Instant.EPOCH,
        replaces);
  }

  private String state(ResultCandidate candidate) {
    return jdbc.queryForObject(
        "select state from result_candidate where candidate_id = ?",
        String.class,
        candidate.candidateId());
  }
}
