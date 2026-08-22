package com.sportsbook.settlement.correction;

import com.sportsbook.protocol.domain.BetSlipType;
import com.sportsbook.protocol.domain.SettlementResult;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

record RevisionSnapshot(
    String slipType,
    Integer systemMinWins,
    Integer systemTotalSelections,
    long unitStakeAmount,
    List<Selection> selections) {

  RevisionSnapshot {
    selections = List.copyOf(selections);
  }

  static RevisionSnapshot capture(RevisionTarget target) {
    BetSlipType slip = target.slipType();
    String type = slip instanceof BetSlipType.Single ? "SINGLE" : "MULTIPLE";
    Integer minimumWins = null;
    Integer totalSelections = null;
    if (slip instanceof BetSlipType.System system) {
      type = "SYSTEM";
      minimumWins = system.minWins();
      totalSelections = system.totalSelections();
    }
    List<Selection> selections = new ArrayList<>();
    for (int index = 0; index < target.selections().size(); index++) {
      var selection = target.selections().get(index);
      selections.add(
          new Selection(
              selection.selectionId(), index, selection.odds().decimal(), selection.outcome()));
    }
    return new RevisionSnapshot(
        type, minimumWins, totalSelections, target.unitStake().amount(), selections);
  }

  record Selection(UUID selectionId, int legIndex, BigDecimal odds, SettlementResult outcome) {}
}
