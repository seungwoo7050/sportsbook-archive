package com.sportsbook.settlement.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.settlement.client.WalletAdjustmentProof;
import com.sportsbook.settlement.correction.RevisionLease;
import com.sportsbook.settlement.correction.RevisionPlanRepository;
import com.sportsbook.settlement.correction.RevisionState;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PostgresBlockedExhaustionIntegrationTest extends PostgresIntegrationSupport {

  @Autowired private RevisionPlanRepository revisions;

  @Test
  void pausesABlockedProofAtAttemptTwelve() {
    Seed seed = insertLeasedRevision();

    assertThat(
            revisions.markBlocked(
                seed.revisionId(), seed.lease(), blocked(seed), Instant.EPOCH.plusSeconds(2)))
        .contains(RevisionState.BLOCKED);
    assertThat(
            jdbc.queryForObject(
                "select state='BLOCKED' and last_error_code='WALLET_RETRY_EXHAUSTED' "
                    + "and lease_token is null and lease_until is null "
                    + "and wallet_status='BLOCKED' and wallet_queue_sequence=7 "
                    + "and wallet_next_attempt_at is not null and next_retry_at is null "
                    + "from settlement_revision where revision_id=?",
                Boolean.class,
                seed.revisionId()))
        .isTrue();
  }

  private WalletAdjustmentProof blocked(Seed seed) {
    return new WalletAdjustmentProof(
        seed.revisionId(),
        seed.betId(),
        1,
        seed.userId(),
        Money.krw(200),
        Money.krw(100),
        -100,
        Currency.KRW,
        WalletAdjustmentProof.Status.BLOCKED,
        7L,
        null,
        Instant.EPOCH,
        null,
        Instant.EPOCH.plusSeconds(30));
  }

  private Seed insertLeasedRevision() {
    PendingBet bet = insertPendingBet(UUID.randomUUID());
    UUID candidateId = UUID.randomUUID();
    UUID revisionId = UUID.randomUUID();
    UUID token = UUID.randomUUID();
    jdbc.update(
        "insert into result_candidate (candidate_id,event_id,fingerprint,mode,settled_at,"
            + "received_at,state,decided_at) values (?,?,?,'COMPLETED',current_timestamp,"
            + "current_timestamp,'ACCEPTED',current_timestamp)",
        candidateId,
        bet.eventId(),
        candidateId.toString().replace("-", "").repeat(2));
    jdbc.update(
        "update bet set status='SETTLED',result='WON',payout_amount=200,"
            + "payout_currency='KRW',settled_at=current_timestamp where bet_id=?",
        bet.betId());
    jdbc.update(
        "insert into settlement_revision (revision_id,bet_id,revision_number,user_id,event_id,"
            + "source_candidate_id,previous_result,new_result,previous_payout_amount,"
            + "new_payout_amount,currency,slip_type,unit_stake_amount,source_result_settled_at,"
            + "state,lease_token,lease_until,"
            + "attempt_count,next_retry_at,created_at,updated_at) values (?,?,1,?,?,?,'WON',"
            + "'PUSH',200,100,'KRW','SINGLE',100,current_timestamp,'PENDING',?,"
            + "current_timestamp+interval "
            + "'30 seconds',12,null,current_timestamp,current_timestamp)",
        revisionId,
        bet.betId(),
        bet.userId(),
        bet.eventId(),
        candidateId,
        token);
    return new Seed(revisionId, bet.betId(), bet.userId(), new RevisionLease(token, Instant.MAX));
  }

  private record Seed(UUID revisionId, UUID betId, UUID userId, RevisionLease lease) {}
}
