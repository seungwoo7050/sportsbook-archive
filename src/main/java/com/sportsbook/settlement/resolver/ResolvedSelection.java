package com.sportsbook.settlement.resolver;

import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.protocol.value.Odds;
import java.util.Objects;
import java.util.UUID;

/** Immutable settle-time leg snapshot used by payout resolution. */
public record ResolvedSelection(UUID selectionId, Odds odds, SettlementResult outcome) {

  public ResolvedSelection {
    Objects.requireNonNull(selectionId, "selectionId");
    Objects.requireNonNull(odds, "odds");
    Objects.requireNonNull(outcome, "outcome");
  }

  public boolean wins() {
    return outcome == SettlementResult.WON;
  }

  public boolean returnsStake() {
    return outcome == SettlementResult.PUSH || outcome == SettlementResult.VOID;
  }
}
