package com.sportsbook.settlement.resolver;

import com.sportsbook.protocol.domain.BetSlipType;
import java.util.List;
import java.util.Objects;

/** Expands a slip into independently staked payout lines. */
public final class SettlementLineFactory {

  public List<SettlementLine> lines(BetSlipType slipType, List<ResolvedSelection> selections) {
    Objects.requireNonNull(slipType, "slipType");
    List<ResolvedSelection> snapshot = List.copyOf(selections);
    if (slipType instanceof BetSlipType.Single && snapshot.size() != 1) {
      throw new IllegalArgumentException("Single slip requires one resolved selection");
    }
    if (slipType instanceof BetSlipType.Multiple && snapshot.size() < 2) {
      throw new IllegalArgumentException("Multiple slip requires at least two selections");
    }
    if (slipType instanceof BetSlipType.System) {
      throw new IllegalArgumentException("System line expansion requires combination support");
    }
    return List.of(new SettlementLine(0, snapshot));
  }
}
