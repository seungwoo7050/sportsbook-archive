package com.sportsbook.settlement.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class PostgresRevisionConstraintIntegrationTest extends PostgresIntegrationSupport {

  @Test
  void acceptsPausedBlockedProofAndNoProofExhaustion() {
    UUID revisionId = insertPendingRevision();
    jdbc.update(
        "update settlement_revision set state='BLOCKED', attempt_count=12, next_retry_at=null, "
            + "last_error_code='WALLET_RETRY_EXHAUSTED', wallet_status='BLOCKED', "
            + "wallet_queue_sequence=7, wallet_queued_at=current_timestamp, "
            + "wallet_next_attempt_at=current_timestamp where revision_id=?",
        revisionId);
    assertThat(
            jdbc.queryForObject(
                "select state='BLOCKED' and wallet_queue_sequence=7 and next_retry_at is null "
                    + "from settlement_revision where revision_id=?",
                Boolean.class,
                revisionId))
        .isTrue();
    jdbc.update(
        "update settlement_revision set state='EXHAUSTED', wallet_status=null, "
            + "wallet_queue_sequence=null, wallet_queued_at=null, wallet_next_attempt_at=null "
            + "where revision_id=?",
        revisionId);
  }

  @Test
  void rejectsUnknownProofAndInconsistentLeaseShapes() {
    UUID revisionId = insertPendingRevision();
    rejects(
        "update settlement_revision set wallet_next_attempt_at=current_timestamp where revision_id=?",
        revisionId);
    rejects(
        "update settlement_revision set lease_token=? where revision_id=?",
        UUID.randomUUID(),
        revisionId);
    rejects(
        "update settlement_revision set lease_token=?, lease_until=current_timestamp, "
            + "next_retry_at=current_timestamp where revision_id=?",
        UUID.randomUUID(),
        revisionId);
    rejects(
        "update settlement_revision set state='REJECTED', next_retry_at=null, "
            + "last_error_code=null where revision_id=?",
        revisionId);
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

  private void rejects(String sql, Object... arguments) {
    assertThatThrownBy(() -> jdbc.update(sql, arguments))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
