package com.sportsbook.settlement.execution;

import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.Money;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

record SettlementRecoveryRow(
    UUID betId,
    SettlementAttempt.Action action,
    UUID eventId,
    SettlementResult result,
    String voidReason,
    SettlementMoneyPlan money,
    int attemptCount,
    Instant createdAt,
    UUID userId) {

  static SettlementRecoveryRow read(ResultSet row) throws SQLException {
    Currency currency = Currency.valueOf(row.getString("currency"));
    SettlementMoneyPlan money =
        new SettlementMoneyPlan(
            new Money(row.getLong("committed_amount"), currency),
            new Money(row.getLong("payout_amount"), currency),
            new Money(row.getLong("locked_release_amount"), currency),
            new Money(row.getLong("locked_forfeit_amount"), currency),
            new Money(row.getLong("house_profit_amount"), currency));
    String result = row.getString("result");
    return new SettlementRecoveryRow(
        row.getObject("bet_id", UUID.class),
        SettlementAttempt.Action.valueOf(row.getString("action")),
        row.getObject("event_id", UUID.class),
        result == null ? null : SettlementResult.valueOf(result),
        row.getString("void_reason"),
        money,
        row.getInt("attempt_count"),
        row.getTimestamp("created_at").toInstant(),
        row.getObject("user_id", UUID.class));
  }

  SettlementExecution execution(SettlementLease lease, Instant updatedAt) {
    SettlementAttempt attempt =
        new SettlementAttempt(
            betId,
            action,
            eventId,
            result,
            voidReason,
            money,
            lease,
            attemptCount + 1,
            null,
            createdAt,
            updatedAt);
    return new SettlementExecution(attempt, userId);
  }
}
