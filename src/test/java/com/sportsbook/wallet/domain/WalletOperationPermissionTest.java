package com.sportsbook.wallet.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.Money;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class WalletOperationPermissionTest {

  private static final Set<Permission> ALLOWED =
      Set.of(
          permission(WalletCaller.PLATFORM, WalletOperationKind.DEPOSIT),
          permission(WalletCaller.PLATFORM, WalletOperationKind.WITHDRAW),
          permission(WalletCaller.BETTING, WalletOperationKind.BET_DEBIT),
          permission(WalletCaller.BETTING, WalletOperationKind.BET_REFUND),
          permission(WalletCaller.SETTLEMENT, WalletOperationKind.BET_PAYOUT),
          permission(WalletCaller.SETTLEMENT, WalletOperationKind.BET_REFUND),
          permission(WalletCaller.SETTLEMENT, WalletOperationKind.BET_FORFEIT),
          permission(WalletCaller.SETTLEMENT, WalletOperationKind.BET_ADJUSTMENT),
          permission(WalletCaller.ADMIN, WalletOperationKind.BET_REFUND));

  @ParameterizedTest(name = "{0} calling {1}")
  @MethodSource("callerKinds")
  void enforcesCallerOperationPermissions(WalletCaller caller, WalletOperationKind kind) {
    ThrowingCallable attempt = () -> operation(caller, kind);

    if (ALLOWED.contains(permission(caller, kind))) {
      assertThatCode(attempt).doesNotThrowAnyException();
    } else {
      assertThatThrownBy(attempt).isInstanceOf(IllegalArgumentException.class);
    }
  }

  static Stream<Arguments> callerKinds() {
    return Arrays.stream(WalletCaller.values())
        .flatMap(
            caller ->
                Arrays.stream(WalletOperationKind.values())
                    .map(kind -> Arguments.of(caller, kind)));
  }

  private static WalletOperation operation(WalletCaller caller, WalletOperationKind kind) {
    return WalletOperation.succeeded(
        IdempotencyKey.of("caller-permission"),
        caller,
        kind,
        UUID.fromString("019b76da-a000-7000-8000-000000000023"),
        Money.krw(100L),
        "e".repeat(64),
        UUID.fromString("019b76da-a000-7000-8000-000000000024"),
        Instant.parse("2026-01-01T00:00:00Z"));
  }

  private static Permission permission(WalletCaller caller, WalletOperationKind kind) {
    return new Permission(caller, kind);
  }

  private record Permission(WalletCaller caller, WalletOperationKind kind) {}
}
