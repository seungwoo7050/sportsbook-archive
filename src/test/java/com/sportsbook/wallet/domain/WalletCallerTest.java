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

  @Test
  void mapsOnlyExactWireNames() {
    assertThat(WalletCaller.values())
        .extracting(WalletCaller::wireName)
        .containsExactly(
            "platform", "gateway", "betting-service", "settlement-service", "admin-api");
    assertThat(WalletCaller.fromWireName("platform")).contains(WalletCaller.PLATFORM);
    assertThat(WalletCaller.fromWireName("betting-service")).contains(WalletCaller.BETTING);
    assertThat(WalletCaller.fromWireName("settlement-service")).contains(WalletCaller.SETTLEMENT);

    assertThat(WalletCaller.fromWireName(null)).isEmpty();
    assertThat(WalletCaller.fromWireName("PLATFORM")).isEmpty();
    assertThat(WalletCaller.fromWireName(" PLATFORM ")).isEmpty();
    assertThat(WalletCaller.fromWireName("unknown")).isEmpty();
  }
}
