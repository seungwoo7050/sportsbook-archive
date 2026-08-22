package com.sportsbook.betting.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.domain.BetSlipType;
import org.junit.jupiter.api.Test;

class SlipKindTest {

  @Test
  void mapsEverySharedSlipVariant() {
    assertThat(SlipKind.of(new BetSlipType.Single())).isEqualTo(SlipKind.SINGLE);
    assertThat(SlipKind.of(new BetSlipType.Multiple())).isEqualTo(SlipKind.MULTIPLE);
    assertThat(SlipKind.of(new BetSlipType.System(2, 3))).isEqualTo(SlipKind.SYSTEM);
  }
}
