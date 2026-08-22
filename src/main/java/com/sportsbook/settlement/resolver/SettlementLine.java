package com.sportsbook.settlement.resolver;

import java.util.List;

/** One independently staked line in deterministic combination order. */
public record SettlementLine(int ordinal, List<ResolvedSelection> selections) {

  public SettlementLine {
    if (ordinal < 0 || selections == null || selections.isEmpty()) {
      throw new IllegalArgumentException("Settlement line requires an ordinal and selections");
    }
    selections = List.copyOf(selections);
  }
}
