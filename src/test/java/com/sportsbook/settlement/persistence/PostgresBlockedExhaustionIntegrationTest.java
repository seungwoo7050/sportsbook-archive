package com.sportsbook.settlement.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.settlement.client.WalletAdjustmentProof;
import com.sportsbook.settlement.client.WalletFailurePolicy;
import com.sportsbook.settlement.correction.RevisionLease;
import com.sportsbook.settlement.correction.RevisionPlanRepository;
import com.sportsbook.settlement.correction.RevisionState;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;

class PostgresBlockedExhaustionIntegrationTest extends PostgresIntegrationSupport {

  @Autowired private RevisionPlanRepository revisions;

  @Test
  void pausesABlockedProofAtAttemptTwelve() {
    Seed seed = insertLeasedRevision(12);

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

  @Test
  void pausesAMissingBlockedProofBehindTheLeaseFence() throws Exception {
    Seed seed = insertLeasedRevision(5);
    jdbc.update(
        "update settlement_revision set wallet_status='BLOCKED', wallet_queue_sequence=7, "
            + "wallet_queued_at=current_timestamp, "
            + "wallet_next_attempt_at=current_timestamp where revision_id=?",
        seed.revisionId());
    WalletFailurePolicy.PermanentFailure missing = missingAdjustment();
    RevisionLease wrong = new RevisionLease(UUID.randomUUID(), Instant.MAX);

    assertThat(revisions.rejectPermanent(seed.revisionId(), wrong, missing, Instant.EPOCH))
        .isEmpty();
    assertThat(revisions.rejectPermanent(seed.revisionId(), seed.lease(), missing, Instant.EPOCH))
        .contains(RevisionState.BLOCKED);
    assertThat(
            jdbc.queryForObject(
                "select state='BLOCKED' and last_error_code='WALLET_ADJUSTMENT_NOT_FOUND' "
                    + "and wallet_status='BLOCKED' and wallet_queue_sequence=7 "
                    + "and wallet_next_attempt_at is not null and next_retry_at is null "
                    + "and lease_token is null from settlement_revision where revision_id=?",
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

  private Seed insertLeasedRevision(int attempts) {
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
            + "'30 seconds',?,null,current_timestamp,current_timestamp)",
        revisionId,
        bet.betId(),
        bet.userId(),
        bet.eventId(),
        candidateId,
        token,
        attempts);
    return new Seed(revisionId, bet.betId(), bet.userId(), new RevisionLease(token, Instant.MAX));
  }

  private static WalletFailurePolicy.PermanentFailure missingAdjustment() throws Exception {
    ClientHttpResponse response = org.mockito.Mockito.mock(ClientHttpResponse.class);
    org.mockito.Mockito.when(response.getStatusCode()).thenReturn(HttpStatus.NOT_FOUND);
    org.mockito.Mockito.when(response.getBody())
        .thenReturn(
            new ByteArrayInputStream(
                "{\"errorCode\":\"WALLET_ADJUSTMENT_NOT_FOUND\"}"
                    .getBytes(StandardCharsets.UTF_8)));
    try {
      WalletFailurePolicy.throwFor(response);
      throw new AssertionError("Expected missing Wallet adjustment");
    } catch (WalletFailurePolicy.PermanentFailure failure) {
      return failure;
    }
  }

  private record Seed(UUID revisionId, UUID betId, UUID userId, RevisionLease lease) {}
}
