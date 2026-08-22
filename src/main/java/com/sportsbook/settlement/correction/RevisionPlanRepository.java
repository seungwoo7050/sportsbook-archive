package com.sportsbook.settlement.correction;

import static com.sportsbook.settlement.persistence.JdbcTimestamps.required;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class RevisionPlanRepository {

  private final JdbcTemplate jdbc;

  public RevisionPlanRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional
  public Persisted persist(RevisionPlan plan, Duration leaseDuration) {
    long leaseMillis = leaseDuration == null ? 0 : leaseDuration.toMillis();
    if (leaseMillis < 1) {
      throw new IllegalArgumentException("Revision lease duration must be positive");
    }
    RevisionTarget target = plan.target();
    RevisionSnapshot snapshot = RevisionSnapshot.capture(target);
    UUID leaseToken = UUID.randomUUID();
    List<Timestamp> claimed =
        jdbc.query(
            """
            insert into settlement_revision (
                revision_id, bet_id, revision_number, user_id, event_id, source_candidate_id,
                previous_result, new_result, previous_payout_amount, new_payout_amount, currency,
                slip_type, system_min_wins, system_total_selections, unit_stake_amount,
                source_result_settled_at, state, lease_token, lease_until, attempt_count,
                next_retry_at, created_at, updated_at)
            select ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', ?,
                current_timestamp + (? * interval '1 millisecond'), 1, null, ?, ?
            from bet where bet_id = ? and status = 'SETTLED' and revision_number = ?
            on conflict do nothing
            returning lease_until
            """,
            (result, rowNumber) -> result.getTimestamp("lease_until"),
            plan.revisionId(),
            target.betId(),
            target.revisionNumber(),
            target.userId(),
            target.eventId(),
            target.sourceCandidateId(),
            target.previousResult().name(),
            plan.newResult().name(),
            target.previousPayout().amount(),
            plan.newPayout().amount(),
            plan.newPayout().currency().name(),
            snapshot.slipType(),
            snapshot.systemMinWins(),
            snapshot.systemTotalSelections(),
            snapshot.unitStakeAmount(),
            required(target.sourceResultSettledAt()),
            leaseToken,
            leaseMillis,
            required(plan.createdAt()),
            required(plan.createdAt()),
            target.betId(),
            target.revisionNumber() - 1);
    if (!claimed.isEmpty()) {
      jdbc.batchUpdate(
          """
          insert into settlement_revision_selection (
              revision_id, selection_id, leg_index, odds, outcome)
          values (?, ?, ?, ?, ?)
          """,
          RevisionSelectionRows.from(plan.revisionId(), snapshot));
      return new Persisted(
          plan.revisionId(), true, new RevisionLease(leaseToken, claimed.get(0).toInstant()));
    }
    UUID existing =
        jdbc
            .query(
                """
                select revision_id from settlement_revision
                where bet_id = ? and source_candidate_id = ?
                """,
                (result, rowNumber) -> result.getObject("revision_id", UUID.class),
                target.betId(),
                target.sourceCandidateId())
            .stream()
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Concurrent revision allocation lost"));
    return new Persisted(existing, false, null);
  }

  public record Persisted(UUID revisionId, boolean created, RevisionLease lease) {}
}
