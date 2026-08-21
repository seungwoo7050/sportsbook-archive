package com.sportsbook.protocol.domain;

import com.sportsbook.protocol.value.BetId;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * shared-protocol holds only structural invariants — "is this BetSlip self-consistent as a data
 * shape?". Domain validation (ADR-0008 L1 Same Market, L2 Same Event, L4/L5 policy bounds, odds
 * slippage tolerance) lives in betting-service's BetSlipValidator. The invariants here prevent the
 * wire from carrying nonsensical shapes (Single with 3 selections, SETTLED without a result, etc.)
 * which protect every consumer downstream.
 */
public record BetSlip(
    BetId id,
    UserId userId,
    BetSlipType type,
    List<BetSelection> selections,
    Money stake,
    BetStatus status,
    Instant placedAt,
    SettlementResult settlementResult,
    Instant settledAt,
    Money payout) {

  public static final int MULTIPLE_MIN_SELECTIONS = 2;

  public BetSlip {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(userId, "userId");
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(selections, "selections");
    Objects.requireNonNull(stake, "stake");
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(placedAt, "placedAt");

    if (selections.isEmpty()) {
      throw new IllegalArgumentException("BetSlip must have at least one selection");
    }
    if (!stake.isPositive()) {
      throw new IllegalArgumentException("BetSlip stake must be positive (got " + stake + ")");
    }

    if (type instanceof BetSlipType.Single && selections.size() != 1) {
      throw new IllegalArgumentException(
          "Single slip must have exactly 1 selection (got " + selections.size() + ")");
    }
    if (type instanceof BetSlipType.Multiple && selections.size() < MULTIPLE_MIN_SELECTIONS) {
      throw new IllegalArgumentException(
          "Multiple slip must have at least "
              + MULTIPLE_MIN_SELECTIONS
              + " selections (got "
              + selections.size()
              + ")");
    }
    if (type instanceof BetSlipType.System sys && selections.size() != sys.totalSelections()) {
      throw new IllegalArgumentException(
          "System slip selections.size ("
              + selections.size()
              + ") must equal type.totalSelections ("
              + sys.totalSelections()
              + ")");
    }

    if (status == BetStatus.SETTLED) {
      if (settlementResult == null) {
        throw new IllegalArgumentException("SETTLED slip must have settlementResult");
      }
      if (settledAt == null) {
        throw new IllegalArgumentException("SETTLED slip must have settledAt");
      }
    } else {
      if (settlementResult != null) {
        throw new IllegalArgumentException(
            "Non-SETTLED slip must not have settlementResult (status=" + status + ")");
      }
      if (settledAt != null) {
        throw new IllegalArgumentException(
            "Non-SETTLED slip must not have settledAt (status=" + status + ")");
      }
      if (payout != null) {
        throw new IllegalArgumentException(
            "Non-SETTLED slip must not have payout (status=" + status + ")");
      }
    }

    if (settlementResult == SettlementResult.WON
        || settlementResult == SettlementResult.PUSH
        || settlementResult == SettlementResult.VOID) {
      if (payout == null) {
        throw new IllegalArgumentException(
            settlementResult + " slip must have payout (winnings or refund)");
      }
    } else if (settlementResult == SettlementResult.LOST && payout != null) {
      throw new IllegalArgumentException("LOST slip must not have payout");
    }

    // Defensive copy: prevent external mutation of the selections list after construction.
    selections = List.copyOf(selections);
  }
}
