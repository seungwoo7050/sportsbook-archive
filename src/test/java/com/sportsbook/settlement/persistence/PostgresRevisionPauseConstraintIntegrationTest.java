package com.sportsbook.settlement.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class PostgresRevisionPauseConstraintIntegrationTest extends PostgresIntegrationSupport {

  @Test
  void requiresVisibleErrorAndProofWhenPausingBlockedAutomation() {
    UUID accepted = insertPendingRevision();
    jdbc.update(
        "update settlement_revision set state='BLOCKED', next_retry_at=null, "
            + "last_error_code='WALLET_ADJUSTMENT_NOT_FOUND', wallet_status='BLOCKED', "
            + "wallet_queue_sequence=8, wallet_queued_at=current_timestamp, "
            + "wallet_next_attempt_at=current_timestamp where revision_id=?",
        accepted);
    assertThat(
            jdbc.queryForObject(
                "select state='BLOCKED' and attempt_count=1 and next_retry_at is null "
                    + "from settlement_revision where revision_id=?",
                Boolean.class,
                accepted))
        .isTrue();

    UUID missingError = insertPendingRevision();
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update settlement_revision set state='BLOCKED', next_retry_at=null, "
                        + "wallet_status='BLOCKED', wallet_queue_sequence=1, "
                        + "wallet_queued_at=current_timestamp, "
                        + "wallet_next_attempt_at=current_timestamp where revision_id=?",
                    missingError))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private UUID insertPendingRevision() {
    PendingBet bet = insertPendingBet(UUID.randomUUID());
    UUID candidateId = UUID.randomUUID();
    UUID revisionId = UUID.randomUUID();
    jdbc.update(
        "insert into result_candidate (candidate_id,event_id,fingerprint,mode,settled_at,"
            + "received_at,state,decided_at) values (?,?,?,'COMPLETED',current_timestamp,"
            + "current_timestamp,'ACCEPTED',current_timestamp)",
        candidateId,
        bet.eventId(),
        candidateId.toString().replace("-", "").repeat(2));
    jdbc.update(
        "insert into settlement_revision (revision_id,bet_id,revision_number,user_id,event_id,"
            + "source_candidate_id,previous_result,new_result,previous_payout_amount,"
            + "new_payout_amount,currency,slip_type,unit_stake_amount,source_result_settled_at,"
            + "state,attempt_count,next_retry_at,created_at,updated_at) values (?,?,1,?,?,?,"
            + "'WON','PUSH',200,100,'KRW','SINGLE',100,current_timestamp,'PENDING',1,"
            + "current_timestamp,current_timestamp,current_timestamp)",
        revisionId,
        bet.betId(),
        bet.userId(),
        bet.eventId(),
        candidateId);
    return revisionId;
  }
}
