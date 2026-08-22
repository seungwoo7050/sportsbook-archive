package com.sportsbook.settlement.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sportsbook.settlement.admin.AdminCredentials;
import com.sportsbook.settlement.admin.AdminRevisionRetry;
import com.sportsbook.settlement.correction.RevisionRecoveryRepository;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class PostgresRevisionClaimIntegrationTest extends PostgresIntegrationSupport {

  private static final String ADMIN_KEY = "abcdef0123456789abcdef0123456789";

  @Autowired private RevisionRecoveryRepository recovery;
  @Autowired private AdminRevisionRetry adminRetry;
  @Autowired private MockMvc mvc;

  @Test
  void queuesAnExhaustedRevisionOnceBeforeTheScannerClaimsAttemptOne() throws Exception {
    UUID revisionId = insertRevision(11);
    UUID actionKey = UUID.randomUUID();
    jdbc.update(
        "update settlement_revision set state='EXHAUSTED', attempt_count=12, "
            + "next_retry_at=null, last_error_code='WALLET_RETRY_EXHAUSTED' "
            + "where revision_id=?",
        revisionId);

    retry(actionKey, revisionId, "QUEUED");
    retry(actionKey, revisionId, "REPLAY");
    assertThat(
            jdbc.queryForObject(
                "select state='PENDING' and attempt_count=0 and lease_token is null "
                    + "and next_retry_at <= current_timestamp from settlement_revision "
                    + "where revision_id=?",
                Boolean.class,
                revisionId))
        .isTrue();

    var claim = recovery.claimDue(Duration.ofSeconds(30), 1).get(0);

    assertThat(claim.revisionId()).isEqualTo(revisionId);
    assertThat(claim.blockedProof()).isFalse();
    assertThat(
            jdbc.queryForObject(
                "select attempt_count=1 and lease_token is not null and next_retry_at is null "
                    + "from settlement_revision where revision_id=?",
                Boolean.class,
                revisionId))
        .isTrue();
  }

  private void retry(UUID actionKey, UUID revisionId, String outcome) throws Exception {
    mvc.perform(
            post("/internal/admin/revisions/{id}/retry", revisionId)
                .header("Idempotency-Key", actionKey)
                .header(AdminCredentials.SERVICE_HEADER, AdminCredentials.CALLER)
                .header(AdminCredentials.API_KEY_HEADER, ADMIN_KEY))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.outcome").value(outcome));
  }

  @Test
  void preservesAPausedBlockedProofUntilItsWalletDueTime() {
    UUID revisionId = insertRevision(11);
    jdbc.update(
        "update settlement_revision set state='BLOCKED', attempt_count=12, "
            + "next_retry_at=null, last_error_code='WALLET_RETRY_EXHAUSTED', "
            + "wallet_status='BLOCKED', wallet_queue_sequence=9, "
            + "wallet_queued_at=current_timestamp, "
            + "wallet_next_attempt_at=current_timestamp + interval '1 hour' "
            + "where revision_id=?",
        revisionId);

    adminRetry.claim(UUID.randomUUID(), revisionId);

    assertThat(recovery.claimDue(Duration.ofSeconds(30), 1)).isEmpty();
    assertThat(
            jdbc.queryForObject(
                "select state='BLOCKED' and attempt_count=0 and wallet_status='BLOCKED' "
                    + "and wallet_queue_sequence=9 and next_retry_at=wallet_next_attempt_at "
                    + "from settlement_revision where revision_id=?",
                Boolean.class,
                revisionId))
        .isTrue();
  }

  @Test
  void claimsDuePendingAndBlockedRowsButExcludesFutureAndExhaustedWork() {
    UUID pending = insertRevision(1);
    UUID blocked = insertRevision(4);
    UUID blockedFuture = insertRevision(3);
    UUID pausedBlocked = insertRevision(5);
    UUID future = insertRevision(2);
    UUID exhausted = insertRevision(11);
    jdbc.update(
        "update settlement_revision set state='BLOCKED', wallet_status='BLOCKED', "
            + "wallet_queue_sequence=1, wallet_queued_at=current_timestamp, "
            + "wallet_next_attempt_at=current_timestamp - interval '1 second' "
            + "where revision_id=?",
        blocked);
    jdbc.update(
        "update settlement_revision set state='BLOCKED', wallet_status='BLOCKED', "
            + "wallet_queue_sequence=2, wallet_queued_at=current_timestamp, "
            + "wallet_next_attempt_at=current_timestamp + interval '1 hour' "
            + "where revision_id=?",
        blockedFuture);
    jdbc.update(
        "update settlement_revision set state='BLOCKED', attempt_count=12, next_retry_at=null, "
            + "last_error_code='WALLET_RETRY_EXHAUSTED', wallet_status='BLOCKED', "
            + "wallet_queue_sequence=3, wallet_queued_at=current_timestamp, "
            + "wallet_next_attempt_at=current_timestamp where revision_id=?",
        pausedBlocked);
    jdbc.update(
        "update settlement_revision set state='EXHAUSTED', attempt_count=12, "
            + "next_retry_at=null, last_error_code='WALLET_RETRY_EXHAUSTED' where revision_id=?",
        exhausted);
    jdbc.update(
        "update settlement_revision set next_retry_at=current_timestamp + interval '1 hour' "
            + "where revision_id=?",
        future);

    var claims = recovery.claimDue(Duration.ofSeconds(30), 10);

    assertThat(claims)
        .extracting(RevisionRecoveryRepository.Claim::revisionId)
        .containsExactlyInAnyOrder(pending, blocked)
        .doesNotContain(blockedFuture, pausedBlocked, future, exhausted);
    assertThat(claims)
        .filteredOn(claim -> claim.revisionId().equals(blocked))
        .first()
        .extracting(RevisionRecoveryRepository.Claim::blockedProof)
        .isEqualTo(true);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from settlement_revision where revision_id in (?, ?) "
                    + "and state='PENDING' and attempt_count in (2, 5) "
                    + "and lease_token is not null and lease_until > current_timestamp "
                    + "and next_retry_at is null",
                Integer.class,
                pending,
                blocked))
        .isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "select wallet_status='BLOCKED' and wallet_queue_sequence=1 "
                    + "and wallet_next_attempt_at is not null from settlement_revision "
                    + "where revision_id=?",
                Boolean.class,
                blocked))
        .isTrue();
  }

  private UUID insertRevision(int attempts) {
    PendingBet bet = insertPendingBet(UUID.randomUUID());
    UUID candidateId = UUID.randomUUID();
    UUID revisionId = UUID.randomUUID();
    String fingerprint = candidateId.toString().replace("-", "").repeat(2);
    jdbc.update(
        "insert into result_candidate (candidate_id, event_id, fingerprint, mode, settled_at, "
            + "received_at, state, decided_at) values (?, ?, ?, 'COMPLETED', "
            + "current_timestamp, current_timestamp, 'ACCEPTED', current_timestamp)",
        candidateId,
        bet.eventId(),
        fingerprint);
    jdbc.update(
        "update bet set status='SETTLED', result='WON', payout_amount=200, "
            + "payout_currency='KRW', settled_at=current_timestamp where bet_id=?",
        bet.betId());
    jdbc.update(
        "insert into settlement_revision (revision_id, bet_id, revision_number, user_id, "
            + "event_id, source_candidate_id, previous_result, new_result, "
            + "previous_payout_amount, new_payout_amount, currency, slip_type, "
            + "unit_stake_amount, source_result_settled_at, "
            + "state, attempt_count, next_retry_at, created_at, updated_at) "
            + "values (?, ?, 1, ?, ?, ?, 'WON', 'PUSH', 200, 100, 'KRW', 'SINGLE', 100, "
            + "current_timestamp, "
            + "'PENDING', ?, current_timestamp - interval '1 second', current_timestamp, "
            + "current_timestamp)",
        revisionId,
        bet.betId(),
        bet.userId(),
        bet.eventId(),
        candidateId,
        attempts);
    return revisionId;
  }
}
