package com.sportsbook.settlement.correction;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.domain.BetSlipType;
import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.Odds;
import com.sportsbook.settlement.resolver.ResolvedSelection;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RevisionSnapshotTest {

  @Test
  void capturesSystemShapeSelectionOrderAndOdds() {
    var first = selection("2.0000", SettlementResult.WON);
    var second = selection("1.5000", SettlementResult.PUSH);
    RevisionTarget target =
        new RevisionTarget(
            UUID.randomUUID(),
            1,
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            SettlementResult.WON,
            Money.krw(250),
            new BetSlipType.System(1, 2),
            Money.krw(100),
            List.of(first, second),
            Instant.EPOCH);

    RevisionSnapshot snapshot = RevisionSnapshot.capture(target);

    assertThat(snapshot.slipType()).isEqualTo("SYSTEM");
    assertThat(snapshot.systemMinWins()).isEqualTo(1);
    assertThat(snapshot.systemTotalSelections()).isEqualTo(2);
    assertThat(snapshot.unitStakeAmount()).isEqualTo(100);
    assertThat(snapshot.selections())
        .extracting(RevisionSnapshot.Selection::legIndex)
        .containsExactly(0, 1);
    assertThat(snapshot.selections().get(1).odds()).isEqualByComparingTo("1.5000");
  }

  private static ResolvedSelection selection(String odds, SettlementResult outcome) {
    return new ResolvedSelection(UUID.randomUUID(), Odds.ofDecimal(odds), outcome);
  }
}
