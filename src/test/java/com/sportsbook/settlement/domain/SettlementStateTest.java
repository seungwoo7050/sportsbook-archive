package com.sportsbook.settlement.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.domain.BetSlipType;
import org.junit.jupiter.api.Test;

class SettlementStateTest {

  @Test
  void mapsEverySharedSlipShapeWithoutStringParsing() {
    assertThat(SlipKind.from(new BetSlipType.Single())).isEqualTo(SlipKind.SINGLE);
    assertThat(SlipKind.from(new BetSlipType.Multiple())).isEqualTo(SlipKind.MULTIPLE);
    assertThat(SlipKind.from(new BetSlipType.System(2, 3))).isEqualTo(SlipKind.SYSTEM);
    assertThat(SlipKind.SYSTEM.toProtocol(2, 3)).isEqualTo(new BetSlipType.System(2, 3));
  }

  @Test
  void recognizesOnlyCommittedLifecycleStatesAsTerminal() {
    assertThat(SettlementStatus.PENDING.isTerminal()).isFalse();
    assertThat(SettlementStatus.SETTLED.isTerminal()).isTrue();
    assertThat(SettlementStatus.VOIDED.isTerminal()).isTrue();
  }
}
