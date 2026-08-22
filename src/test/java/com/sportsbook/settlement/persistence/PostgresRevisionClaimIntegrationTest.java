package com.sportsbook.settlement.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.settlement.correction.RevisionRecoveryRepository;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PostgresRevisionClaimIntegrationTest extends PostgresIntegrationSupport {

  @Autowired private RevisionRecoveryRepository recovery;

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
