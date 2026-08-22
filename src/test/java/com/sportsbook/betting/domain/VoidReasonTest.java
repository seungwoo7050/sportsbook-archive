package com.sportsbook.betting.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VoidReasonTest {

  @Test
  void mirrorsSharedWireSymbols() {
    assertThat(VoidReason.values())
        .extracting(Enum::name)
        .containsExactly("EVENT_CANCELLED", "EVENT_POSTPONED", "MARKET_VOID", "ADMIN_VOID");
  }
}
