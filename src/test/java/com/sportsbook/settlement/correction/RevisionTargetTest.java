package com.sportsbook.settlement.correction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.sportsbook.protocol.domain.BetSlipType;
import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.Odds;
import com.sportsbook.settlement.resolver.ResolvedSelection;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RevisionTargetTest {

  @Test
  void ownsAnImmutableUnitStakeSnapshot() {
    List<ResolvedSelection> selections = new ArrayList<>(List.of(selection()));
    RevisionTarget target = target(Money.krw(100), selections);

    selections.clear();

    assertThat(target.revisionNumber()).isOne();
    assertThat(target.unitStake()).isEqualTo(Money.krw(100));
    assertThat(target.selections()).containsExactly(selection());
  }

  @Test
  void rejectsMixedSnapshotCurrencies() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> target(Money.usd(100), List.of(selection())));
  }

  private static RevisionTarget target(Money unitStake, List<ResolvedSelection> selections) {
    return new RevisionTarget(
        UUID.randomUUID(),
        1,
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        SettlementResult.WON,
        Money.krw(200),
        new BetSlipType.Single(),
        unitStake,
        selections,
        Instant.EPOCH);
  }

  private static ResolvedSelection selection() {
    return new ResolvedSelection(
        UUID.fromString("00000000-0000-0000-0000-000000000001"),
        Odds.ofDecimal("2.0000"),
        SettlementResult.WON);
  }
}
