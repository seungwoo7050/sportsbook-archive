package com.sportsbook.settlement.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.settlement.correction.ResultCandidate;
import com.sportsbook.settlement.correction.ResultCandidateStore;
import com.sportsbook.settlement.result.MatchOutcomeMode;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class PostgresFirstCandidateRollbackIntegrationTest extends PostgresIntegrationSupport {

  @Autowired private ResultCandidateStore candidates;
  @Autowired private PlatformTransactionManager transactionManager;

  @Test
  void rollsBackTheFirstSnapshotWhenItsCandidateTransitionIsLost() {
    UUID eventId = UUID.randomUUID();
    ResultCandidate first = candidate(eventId);
    candidates.record(first);
    TransactionTemplate transactions = new TransactionTemplate(transactionManager);

    assertThatThrownBy(
            () ->
                transactions.executeWithoutResult(
                    ignored -> {
                      blockUpdate(first.candidateId());
                      candidates.acceptFirst(first.candidateId(), Instant.EPOCH);
                    }))
        .hasRootCauseInstanceOf(IllegalStateException.class);

    assertThat(candidates.findAcceptedCandidateId(eventId)).isEmpty();
    assertThat(state(first)).isEqualTo("PENDING");
    assertThat(jdbc.queryForObject("select count(*) from match_result", Integer.class)).isZero();
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

  private ResultCandidate candidate(UUID eventId) {
    return ResultCandidate.pending(
        eventId,
        "a".repeat(64),
        MatchOutcomeMode.COMPLETED,
        Map.of(UUID.randomUUID(), SettlementResult.WON),
        Instant.EPOCH,
        Instant.EPOCH,
        null);
  }

  private String state(ResultCandidate candidate) {
    return jdbc.queryForObject(
        "select state from result_candidate where candidate_id = ?",
        String.class,
        candidate.candidateId());
  }
}
