package com.sportsbook.settlement.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.settlement.client.WalletFailurePolicy;
import com.sportsbook.settlement.correction.RevisionLease;
import com.sportsbook.settlement.correction.RevisionPlanRepository;
import com.sportsbook.settlement.correction.RevisionState;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PostgresRevisionBackoffIntegrationTest extends PostgresIntegrationSupport {

  @Autowired private RevisionPlanRepository revisions;

  @Test
  void backsOffWithDatabaseTimeAndExhaustsAttemptTwelve() {
    Seed first = insertLeasedRevision(1);
    Seed capped = insertLeasedRevision(11);
    Seed exhausted = insertLeasedRevision(12);

    assertThat(release(first)).contains(RevisionState.PENDING);
    assertDelay(first.revisionId(), "1 second");
    assertThat(release(capped)).contains(RevisionState.PENDING);
    assertDelay(capped.revisionId(), "300 seconds");
    assertThat(release(exhausted)).contains(RevisionState.EXHAUSTED);

    Map<String, Object> row =
        jdbc.queryForMap(
            "select state, last_error_code, lease_token, lease_until "
                + "from settlement_revision where revision_id = ?",
            exhausted.revisionId());
    assertThat(row)
        .containsEntry("state", "EXHAUSTED")
        .containsEntry("last_error_code", "WALLET_RETRY_EXHAUSTED")
        .containsEntry("lease_token", null)
        .containsEntry("lease_until", null);
  }

  private java.util.Optional<RevisionState> release(Seed seed) {
    return revisions.releaseTransient(
        seed.revisionId(), seed.lease(), WalletFailurePolicy.malformedSuccess());
  }

  private void assertDelay(UUID revisionId, String interval) {
    assertThat(
            jdbc.queryForObject(
                "select next_retry_at = updated_at + cast(? as interval) "
                    + "from settlement_revision where revision_id = ?",
                Boolean.class,
                interval,
                revisionId))
        .isTrue();
  }

  private Seed insertLeasedRevision(int attempts) {
    PendingBet bet = insertPendingBet(UUID.randomUUID());
    UUID candidateId = UUID.randomUUID();
    UUID revisionId = UUID.randomUUID();
    UUID leaseToken = UUID.randomUUID();
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
            + "state, lease_token, lease_until, attempt_count, next_retry_at, created_at, "
            + "updated_at) values (?, ?, 1, ?, ?, ?, 'WON', 'PUSH', 200, 100, 'KRW', "
            + "'SINGLE', 100, "
            + "current_timestamp, 'PENDING', ?, current_timestamp + interval '30 seconds', ?, "
            + "null, current_timestamp, current_timestamp)",
        revisionId,
        bet.betId(),
        bet.userId(),
        bet.eventId(),
        candidateId,
        leaseToken,
        attempts);
    return new Seed(revisionId, new RevisionLease(leaseToken, Instant.MAX));
  }

  private record Seed(UUID revisionId, RevisionLease lease) {}
}
