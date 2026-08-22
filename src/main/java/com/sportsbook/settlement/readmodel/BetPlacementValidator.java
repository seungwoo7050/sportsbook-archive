package com.sportsbook.settlement.readmodel;

import com.sportsbook.protocol.domain.BetSlipType;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Enforces the service-owned boundary before a decoded placement reaches storage. */
public final class BetPlacementValidator {

  private static final int MAX_SELECTIONS = 15;

  public BetPlacement validate(BetPlacement placement) {
    if (placement.unitStake().amount() <= 0) {
      throw invalid("unit stake must be positive");
    }
    int count = placement.selections().size();
    if (count < 1 || count > MAX_SELECTIONS) {
      throw invalid("selection count must be in 1..15");
    }
    if (placement.slipType() instanceof BetSlipType.Single && count != 1) {
      throw invalid("single slip requires exactly one selection");
    }
    if (placement.slipType() instanceof BetSlipType.Multiple && count < 2) {
      throw invalid("multiple slip requires at least two selections");
    }
    if (placement.slipType() instanceof BetSlipType.System system
        && system.totalSelections() != count) {
      throw invalid("system total selections must match decoded selections");
    }
    Set<UUID> selected = new HashSet<>();
    for (BetPlacement.Selection selection : placement.selections()) {
      if (!selected.add(selection.selectionId())) {
        throw invalid("selection identifiers must be unique");
      }
    }
    return placement;
  }

  private static PlacementContractException invalid(String detail) {
    return new PlacementContractException("Invalid BetPlacedRequested: " + detail);
  }
}
