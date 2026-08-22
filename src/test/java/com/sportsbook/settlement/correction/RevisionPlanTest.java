package com.sportsbook.settlement.correction;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.domain.BetSlipType;
import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.Odds;
import com.sportsbook.settlement.resolver.ResolvedSelection;
import com.sportsbook.settlement.resolver.SettlementOutcome;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RevisionPlanTest {

  @Test
  void allocatesAUuidV7AndExactSignedDelta() {
    RevisionTarget target = target();

    RevisionPlan plan =
        RevisionPlan.allocate(
            target,
            new SettlementOutcome(SettlementResult.PUSH, Money.krw(100), 1, 1),
            Instant.EPOCH);

    assertThat(plan.revisionId().version()).isEqualTo(7);
    assertThat(plan.target()).isSameAs(target);
    assertThat(plan.deltaAmount()).isEqualTo(-100);
    assertThat(plan.hasZeroDelta()).isFalse();
  }

  @Test
  void bypassesWalletOnlyWhenThePayoutDeltaIsZero() {
    RevisionPlan plan =
        RevisionPlan.allocate(
            target(),
            new SettlementOutcome(SettlementResult.WON, Money.krw(200), 1, 1),
            Instant.EPOCH);

    assertThat(plan.hasZeroDelta()).isTrue();
    assertThat(plan.requiresWalletAdjustment()).isFalse();
  }

  private static RevisionTarget target() {
    return new RevisionTarget(
        UUID.randomUUID(),
        1,
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        SettlementResult.WON,
        Money.krw(200),
        new BetSlipType.Single(),
        Money.krw(100),
        List.of(
            new ResolvedSelection(
                UUID.randomUUID(), Odds.ofDecimal("2.0000"), SettlementResult.PUSH)),
        Instant.EPOCH);
  }
}
