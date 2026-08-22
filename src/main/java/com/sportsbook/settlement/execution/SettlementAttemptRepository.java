package com.sportsbook.settlement.execution;

import static com.sportsbook.settlement.persistence.JdbcTimestamps.required;

import com.sportsbook.settlement.client.WalletFailurePolicy;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class SettlementAttemptRepository {

  private static final String CLAIM_SQL =
      """
      INSERT INTO settlement_attempt (
          bet_id, action, event_id, result, void_reason,
          committed_amount, payout_amount, locked_release_amount,
          locked_forfeit_amount, house_profit_amount, currency,
          lease_token, lease_until, attempt_count, last_error, created_at, updated_at)
      SELECT ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
      FROM bet
      WHERE bet_id = ? AND status = 'PENDING'
      ON CONFLICT (bet_id) DO NOTHING
      """;

  private final JdbcTemplate jdbc;

  public SettlementAttemptRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<SettlementAttempt> claimPending(
      SettlementAttemptDraft draft, Duration leaseDuration) {
    long leaseMillis = leaseDuration == null ? 0 : leaseDuration.toMillis();
    if (leaseMillis < 1) {
      throw new IllegalArgumentException("Initial settlement lease must be positive");
    }
    SettlementMoneyPlan money = draft.money();
    UUID token = UUID.randomUUID();
    return jdbc
        .query(
            """
            insert into settlement_attempt (
                bet_id, action, event_id, result, void_reason,
                committed_amount, payout_amount, locked_release_amount,
                locked_forfeit_amount, house_profit_amount, currency,
                lease_token, lease_until, attempt_count, last_error, created_at, updated_at)
            select ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                current_timestamp + (? * interval '1 millisecond'),
                1, null, current_timestamp, current_timestamp
            from bet where bet_id = ? and status = 'PENDING'
            on conflict (bet_id) do nothing
            returning lease_until, created_at, updated_at
            """,
            (result, rowNumber) ->
                new InitialClock(
                    result.getTimestamp("lease_until").toInstant(),
                    result.getTimestamp("created_at").toInstant(),
                    result.getTimestamp("updated_at").toInstant()),
            draft.betId(),
            draft.action().name(),
            draft.eventId(),
            draft.result() == null ? null : draft.result().name(),
            draft.voidReason(),
            money.committed().amount(),
            money.payout().amount(),
            money.lockedRelease().amount(),
            money.lockedForfeit().amount(),
            money.houseProfit().amount(),
            money.committed().currency().name(),
            token,
            leaseMillis,
            draft.betId())
        .stream()
        .findFirst()
        .map(
            clock ->
                draft.claimed(
                    new SettlementLease(token, clock.leaseUntil()),
                    clock.createdAt(),
                    clock.updatedAt()));
  }

  public boolean claimPending(SettlementAttempt attempt) {
    SettlementMoneyPlan money = attempt.money();
    int inserted =
        jdbc.update(
            CLAIM_SQL,
            attempt.betId(),
            attempt.action().name(),
            attempt.eventId(),
            attempt.result() == null ? null : attempt.result().name(),
            attempt.voidReason(),
            money.committed().amount(),
            money.payout().amount(),
            money.lockedRelease().amount(),
            money.lockedForfeit().amount(),
            money.houseProfit().amount(),
            money.committed().currency().name(),
            attempt.lease().token(),
            required(attempt.lease().until()),
            attempt.attemptCount(),
            attempt.lastError(),
            required(attempt.createdAt()),
            required(attempt.updatedAt()),
            attempt.betId());
    return inserted == 1;
  }

  public boolean consumeLease(SettlementAttempt attempt) {
    return jdbc.update(
            "delete from settlement_attempt where bet_id = ? and action = ? and lease_token = ?",
            attempt.betId(),
            attempt.action().name(),
            attempt.lease().token())
        == 1;
  }

  public boolean releaseForRecovery(SettlementAttempt attempt, Throwable failure, Instant now) {
    String summary =
        failure instanceof WalletFailurePolicy.Failure walletFailure
            ? "WalletFailure:" + walletFailure.errorCode()
            : failure.getClass().getSimpleName();
    return jdbc.update(
            """
            update settlement_attempt
            set lease_token = null, lease_until = null, last_error = ?, updated_at = ?
            where bet_id = ? and lease_token = ?
            """,
            summary,
            required(now),
            attempt.betId(),
            attempt.lease().token())
        == 1;
  }

  @Transactional
  public List<SettlementExecution> claimRecoveryBatch(Duration leaseDuration, int limit) {
    long leaseMillis = leaseDuration == null ? 0 : leaseDuration.toMillis();
    if (leaseMillis < 1 || limit < 1 || limit > 1000) {
      throw new IllegalArgumentException("Recovery batch size must be between 1 and 1000");
    }
    List<SettlementRecoveryRow> rows =
        jdbc.query(
            """
            select a.*, b.user_id
            from settlement_attempt a join bet b on b.bet_id = a.bet_id
            where b.status = 'PENDING'
                and (a.lease_until is null or a.lease_until <= current_timestamp)
            order by a.updated_at, a.bet_id limit ? for update of a skip locked
            """,
            (result, rowNumber) -> SettlementRecoveryRow.read(result),
            limit);
    List<SettlementExecution> executions = new ArrayList<>(rows.size());
    for (SettlementRecoveryRow row : rows) {
      UUID token = UUID.randomUUID();
      RecoveryClock clock =
          jdbc
              .query(
                  """
                  update settlement_attempt set lease_token = ?,
                      lease_until = current_timestamp + (? * interval '1 millisecond'),
                      attempt_count = attempt_count + 1, last_error = null,
                      updated_at = current_timestamp
                  where bet_id = ? returning lease_until, updated_at
                  """,
                  (result, rowNumber) ->
                      new RecoveryClock(
                          result.getTimestamp("lease_until").toInstant(),
                          result.getTimestamp("updated_at").toInstant()),
                  token,
                  leaseMillis,
                  row.betId())
              .stream()
              .findFirst()
              .orElseThrow(() -> new IllegalStateException("Recovery claim lost locked attempt"));
      SettlementLease lease = new SettlementLease(token, clock.leaseUntil());
      executions.add(row.execution(lease, clock.updatedAt()));
    }
    return List.copyOf(executions);
  }

  private record RecoveryClock(Instant leaseUntil, Instant updatedAt) {}

  private record InitialClock(Instant leaseUntil, Instant createdAt, Instant updatedAt) {}
}
