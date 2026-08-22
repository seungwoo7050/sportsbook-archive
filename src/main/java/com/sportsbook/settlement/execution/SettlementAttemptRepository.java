package com.sportsbook.settlement.execution;

import static com.sportsbook.settlement.persistence.JdbcTimestamps.required;

import com.sportsbook.settlement.client.WalletFailurePolicy;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

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
}
