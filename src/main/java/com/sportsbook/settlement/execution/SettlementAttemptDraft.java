package com.sportsbook.settlement.execution;

import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.protocol.value.Money;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Immutable monetary plan waiting for its first database-owned lease. */
public record SettlementAttemptDraft(
    UUID betId,
    SettlementAttempt.Action action,
    UUID eventId,
    SettlementResult result,
    String voidReason,
    SettlementMoneyPlan money) {

  private static final Set<String> VOID_REASONS =
      Set.of("EVENT_CANCELLED", "EVENT_POSTPONED", "ADMIN_VOID");

  public SettlementAttemptDraft {
    Objects.requireNonNull(betId, "betId");
    Objects.requireNonNull(action, "action");
    Objects.requireNonNull(eventId, "eventId");
    Objects.requireNonNull(money, "money");
    boolean settle =
        action == SettlementAttempt.Action.SETTLE && result != null && voidReason == null;
    boolean voided =
        action == SettlementAttempt.Action.VOID
            && result == null
            && VOID_REASONS.contains(voidReason);
    if (!settle && !voided) {
      throw new IllegalArgumentException("Invalid settlement attempt draft");
    }
  }

  public static SettlementAttemptDraft resolved(
      UUID betId, UUID eventId, SettlementResult result, SettlementMoneyPlan money) {
    return new SettlementAttemptDraft(
        betId, SettlementAttempt.Action.SETTLE, eventId, result, null, money);
  }

  public SettlementAttempt claimed(SettlementLease lease, Instant createdAt, Instant updatedAt) {
    return new SettlementAttempt(
        betId, action, eventId, result, voidReason, money, lease, 1, null, createdAt, updatedAt);
  }

  public static SettlementAttemptDraft wholeSlipVoid(
      UUID betId, UUID eventId, String reason, Money totalExposure) {
    Money zero = Money.zero(totalExposure.currency());
    var refund = new SettlementMoneyPlan(totalExposure, totalExposure, totalExposure, zero, zero);
    return new SettlementAttemptDraft(
        betId, SettlementAttempt.Action.VOID, eventId, null, reason, refund);
  }
}
