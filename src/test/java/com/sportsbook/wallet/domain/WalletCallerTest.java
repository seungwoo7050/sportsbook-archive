package com.sportsbook.wallet.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WalletCallerTest {

  @Test
  void locksWalletCallerVocabulary() {
    assertThat(WalletCaller.values())
        .containsExactly(
            WalletCaller.PLATFORM,
            WalletCaller.GATEWAY,
            WalletCaller.BETTING,
            WalletCaller.SETTLEMENT,
            WalletCaller.ADMIN);
  }
}
