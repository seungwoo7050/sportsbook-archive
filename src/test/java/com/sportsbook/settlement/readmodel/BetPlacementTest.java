package com.sportsbook.settlement.readmodel;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.domain.BetSlipType;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.Odds;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BetPlacementTest {

  @Test
  void ownsAnImmutableDecodedSelectionSnapshot() {
    BetPlacement.Selection selection =
        new BetPlacement.Selection(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Odds.ofDecimal("1.7500"));
    List<BetPlacement.Selection> source = new ArrayList<>(List.of(selection));

    BetPlacement placement =
        new BetPlacement(
            UUID.randomUUID(),
            UUID.randomUUID(),
            new BetSlipType.Single(),
            Money.krw(500),
            Instant.EPOCH,
            source);
    source.clear();

    assertThat(placement.unitStake()).isEqualTo(Money.krw(500));
    assertThat(placement.selections()).containsExactly(selection).isUnmodifiable();
  }
}
