package com.sportsbook.settlement.resolver;

import com.sportsbook.protocol.domain.BetSlipType;
import java.util.ArrayList;
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
    if (slipType instanceof BetSlipType.System system) {
      if (system.totalSelections() != snapshot.size()) {
        throw new IllegalArgumentException("System N must match resolved selections");
      }
      List<SettlementLine> combinations = new ArrayList<>();
      choose(snapshot, system.minWins(), 0, new ArrayList<>(), combinations);
      return List.copyOf(combinations);
    }
    return List.of(new SettlementLine(0, snapshot));
  }

  private static void choose(
      List<ResolvedSelection> selections,
      int remaining,
      int next,
      List<ResolvedSelection> chosen,
      List<SettlementLine> lines) {
    if (remaining == 0) {
      lines.add(new SettlementLine(lines.size(), chosen));
      return;
    }
    int lastStart = selections.size() - remaining;
    for (int index = next; index <= lastStart; index++) {
      chosen.add(selections.get(index));
      choose(selections, remaining - 1, index + 1, chosen, lines);
      chosen.remove(chosen.size() - 1);
    }
  }
}
