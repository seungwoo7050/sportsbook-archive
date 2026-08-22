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

class RevisionResolverTest {

  @Test
  void pricesSystemLinesFromTheOriginalUnitStake() {
    RevisionTarget target =
        new RevisionTarget(
            UUID.randomUUID(),
            1,
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            SettlementResult.LOST,
            Money.krw(0),
            new BetSlipType.System(2, 3),
            Money.krw(100),
            List.of(selection(), selection(), selection()),
            Instant.EPOCH);

    var resolution = new RevisionResolver().resolve(target);

    assertThat(resolution.result()).isEqualTo(SettlementResult.WON);
    assertThat(resolution.payout()).isEqualTo(Money.krw(1_200));
    assertThat(resolution.totalLines()).isEqualTo(3);
  }

  private static ResolvedSelection selection() {
    return new ResolvedSelection(UUID.randomUUID(), Odds.ofDecimal("2.0000"), SettlementResult.WON);
  }
}
