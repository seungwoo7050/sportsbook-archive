package com.sportsbook.wallet.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.sportsbook.wallet.domain.WalletCaller;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class WalletCredentialsTest {
  private static final Map<WalletCaller, String> KEYS =
      Map.of(
          WalletCaller.PLATFORM, "platform:" + "p".repeat(32),
          WalletCaller.GATEWAY, "gateway:" + "g".repeat(32),
          WalletCaller.BETTING, "betting:" + "b".repeat(32),
          WalletCaller.SETTLEMENT, "settlement:" + "s".repeat(32),
          WalletCaller.ADMIN, "admin:" + "a".repeat(32));

  private final WalletCredentials credentials =
      new WalletCredentials(
          new WalletSecurityProperties(
              KEYS.get(WalletCaller.PLATFORM),
              KEYS.get(WalletCaller.GATEWAY),
              KEYS.get(WalletCaller.BETTING),
              KEYS.get(WalletCaller.SETTLEMENT),
              KEYS.get(WalletCaller.ADMIN)));

  @ParameterizedTest
  @EnumSource(WalletCaller.class)
  void authenticatesEveryExactCallerKeyPair(WalletCaller caller) {
    assertThat(credentials.authenticate(caller.wireName(), KEYS.get(caller))).contains(caller);
  }

  @ParameterizedTest
  @MethodSource("crossCallerPairs")
  void rejectsEveryCrossCallerKeyPair(WalletCaller claimed, WalletCaller keyOwner) {
    assertThat(credentials.authenticate(claimed.wireName(), KEYS.get(keyOwner))).isEmpty();
  }

  @Test
  void rejectsMalformedIdentitiesAndKeys() {
    assertThat(credentials.authenticate(null, KEYS.get(WalletCaller.PLATFORM))).isEmpty();
    assertThat(credentials.authenticate("unknown", KEYS.get(WalletCaller.PLATFORM))).isEmpty();
    assertThat(credentials.authenticate("unknown", "wallet-unknown-caller")).isEmpty();
    assertThat(credentials.authenticate("PLATFORM", KEYS.get(WalletCaller.PLATFORM))).isEmpty();
    assertThat(credentials.authenticate("platform", null)).isEmpty();
    assertThat(credentials.authenticate("platform", "wrong-key")).isEmpty();
  }

  private static Stream<Arguments> crossCallerPairs() {
    return Arrays.stream(WalletCaller.values())
        .flatMap(
            claimed ->
                Arrays.stream(WalletCaller.values())
                    .filter(keyOwner -> keyOwner != claimed)
                    .map(keyOwner -> arguments(claimed, keyOwner)));
  }
}
