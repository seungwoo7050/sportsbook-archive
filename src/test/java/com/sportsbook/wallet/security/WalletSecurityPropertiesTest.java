package com.sportsbook.wallet.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.sportsbook.wallet.domain.WalletCaller;
import java.util.EnumMap;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class WalletSecurityPropertiesTest {
  private static final Map<WalletCaller, String> VALID_KEYS =
      Map.of(
          WalletCaller.PLATFORM, "p".repeat(32),
          WalletCaller.GATEWAY, "g".repeat(32),
          WalletCaller.BETTING, "b".repeat(32),
          WalletCaller.SETTLEMENT, "s".repeat(32),
          WalletCaller.ADMIN, "a".repeat(32));

  @Test
  void acceptsFiveDistinctCallerKeys() {
    WalletSecurityProperties properties = create(VALID_KEYS);

    assertThat(WalletCaller.values())
        .allSatisfy(
            caller -> assertThat(properties.apiKey(caller)).isEqualTo(VALID_KEYS.get(caller)));
    assertThatNullPointerException().isThrownBy(() -> properties.apiKey(null));
  }

  @ParameterizedTest
  @EnumSource(WalletCaller.class)
  void rejectsEveryMissingCallerKey(WalletCaller caller) {
    assertInvalid(caller, null, caller.wireName() + " API key");
  }

  @ParameterizedTest
  @EnumSource(WalletCaller.class)
  void rejectsEveryShortCallerKey(WalletCaller caller) {
    assertInvalid(caller, "x".repeat(31), caller.wireName() + " API key");
  }

  @Test
  void rejectsBlankCallerKeys() {
    assertInvalid(WalletCaller.PLATFORM, " ".repeat(32), "platform API key");
  }

  @ParameterizedTest
  @MethodSource("callerPairs")
  void rejectsEveryDuplicatePair(WalletCaller first, WalletCaller second) {
    Map<WalletCaller, String> keys = new EnumMap<>(VALID_KEYS);
    keys.put(second, keys.get(first));

    assertThatIllegalArgumentException()
        .isThrownBy(() -> create(keys))
        .withMessage("Wallet caller API keys must be distinct");
  }

  private static void assertInvalid(WalletCaller caller, String value, String message) {
    Map<WalletCaller, String> keys = new EnumMap<>(VALID_KEYS);
    keys.put(caller, value);
    assertThatIllegalArgumentException()
        .isThrownBy(() -> create(keys))
        .withMessageContaining(message);
  }

  private static WalletSecurityProperties create(Map<WalletCaller, String> keys) {
    return new WalletSecurityProperties(
        keys.get(WalletCaller.PLATFORM),
        keys.get(WalletCaller.GATEWAY),
        keys.get(WalletCaller.BETTING),
        keys.get(WalletCaller.SETTLEMENT),
        keys.get(WalletCaller.ADMIN));
  }

  private static Stream<Arguments> callerPairs() {
    WalletCaller[] callers = WalletCaller.values();
    return IntStream.range(0, callers.length)
        .boxed()
        .flatMap(
            first ->
                IntStream.range(first + 1, callers.length)
                    .mapToObj(second -> arguments(callers[first], callers[second])));
  }
}
