package com.sportsbook.settlement.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.settlement.admin.AdminRevisionQueryRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PostgresAdminRevisionQueryIntegrationTest extends PostgresIntegrationSupport {

  @Autowired private AdminRevisionQueryRepository revisions;

  @Test
  void exposesPausedWalletProofWithoutTheOwnerToken() {
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
            + "state,attempt_count,next_retry_at,last_error_code,wallet_status,"
            + "wallet_queue_sequence,wallet_queued_at,wallet_next_attempt_at,created_at,updated_at) "
            + "values (?,?,1,?,?,?,'WON','PUSH',200,100,'KRW','SINGLE',100,current_timestamp,"
            + "'BLOCKED',12,null,'WALLET_RETRY_EXHAUSTED','BLOCKED',7,current_timestamp,"
            + "current_timestamp,current_timestamp,current_timestamp)",
        revisionId,
        bet.betId(),
        bet.userId(),
        bet.eventId(),
        candidateId);

    AdminRevisionQueryRepository.View view = revisions.find(revisionId).orElseThrow();

    assertThat(view.state()).isEqualTo("BLOCKED");
    assertThat(view.attemptCount()).isEqualTo(12);
    assertThat(view.nextRetryAt()).isNull();
    assertThat(view.lastErrorCode()).isEqualTo("WALLET_RETRY_EXHAUSTED");
    assertThat(view.walletStatus()).isEqualTo("BLOCKED");
    assertThat(view.walletQueueSequence()).isEqualTo(7L);
    assertThat(view.walletQueuedAt()).isNotNull();
    assertThat(view.walletNextAttemptAt()).isNotNull();
    assertThat(AdminRevisionQueryRepository.View.class.getRecordComponents())
        .extracting(component -> component.getName())
        .doesNotContain("leaseToken", "idempotencyKey", "requestFingerprint");
  }
}
