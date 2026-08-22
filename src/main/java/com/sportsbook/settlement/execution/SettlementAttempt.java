package com.sportsbook.settlement.execution;

import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.protocol.value.Money;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record SettlementAttempt(
    UUID betId,
    Action action,
    UUID eventId,
    SettlementResult result,
    String voidReason,
    SettlementMoneyPlan money,
    SettlementLease lease,
    int attemptCount,
    String lastError,
    Instant createdAt,
    Instant updatedAt) {

  private static final Set<String> WHOLE_SLIP_VOID_REASONS =
      Set.of("EVENT_CANCELLED", "EVENT_POSTPONED", "ADMIN_VOID");

  public SettlementAttempt {
    Objects.requireNonNull(betId, "betId");
    Objects.requireNonNull(action, "action");
    Objects.requireNonNull(eventId, "eventId");
    Objects.requireNonNull(money, "money");
    Objects.requireNonNull(lease, "lease");
    Objects.requireNonNull(createdAt, "createdAt");
    Objects.requireNonNull(updatedAt, "updatedAt");
    boolean resolved = action == Action.SETTLE && result != null && voidReason == null;
    boolean voided =
        action == Action.VOID && result == null && WHOLE_SLIP_VOID_REASONS.contains(voidReason);
    if ((!resolved && !voided) || attemptCount < 1) {
      throw new IllegalArgumentException("Invalid settlement attempt action");
    }
  }

  public static SettlementAttempt resolved(
      UUID betId,
      UUID eventId,
      SettlementResult result,
      SettlementMoneyPlan money,
      SettlementLease lease,
      Instant now) {
    return new SettlementAttempt(
        betId, Action.SETTLE, eventId, result, null, money, lease, 1, null, now, now);
  }

  public static SettlementAttempt wholeSlipVoid(
      UUID betId,
      UUID eventId,
      String reason,
      Money totalExposure,
      SettlementLease lease,
      Instant now) {
    Objects.requireNonNull(totalExposure, "totalExposure");
    Money zero = Money.zero(totalExposure.currency());
    SettlementMoneyPlan refund =
        new SettlementMoneyPlan(totalExposure, totalExposure, totalExposure, zero, zero);
    return new SettlementAttempt(
        betId, Action.VOID, eventId, null, reason, refund, lease, 1, null, now, now);
  }

  public enum Action {
    SETTLE,
    VOID
  }
}
