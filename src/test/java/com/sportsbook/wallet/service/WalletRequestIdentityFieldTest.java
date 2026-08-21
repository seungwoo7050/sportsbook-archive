package com.sportsbook.wallet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.wallet.domain.WalletCaller;
import com.sportsbook.wallet.domain.WalletOperation;
import com.sportsbook.wallet.domain.WalletOperationKind;
import com.sportsbook.wallet.domain.error.IdempotencyConflictException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WalletRequestIdentityFieldTest {

  private static final IdempotencyKey KEY = IdempotencyKey.of("identity:baseline");
  private static final UUID USER = UUID.fromString("019b76da-a000-7000-8000-000000000030");
  private static final Money AMOUNT = Money.krw(100L);

  @Test
  void rejectsEveryConflictingRequestFieldAndStoredFingerprint() {
    WalletRequestIdentity baseline =
        identity(KEY, WalletCaller.PLATFORM, WalletOperationKind.DEPOSIT, USER, AMOUNT);
    WalletOperation matching = outcome(KEY, baseline.fingerprint());
    List<WalletRequestIdentity> conflicts =
        List.of(
            identity(
                IdempotencyKey.of("identity:other"),
                WalletCaller.PLATFORM,
                WalletOperationKind.DEPOSIT,
                USER,
                AMOUNT),
            identity(KEY, WalletCaller.BETTING, WalletOperationKind.DEPOSIT, USER, AMOUNT),
            identity(KEY, WalletCaller.PLATFORM, WalletOperationKind.WITHDRAW, USER, AMOUNT),
            identity(
                KEY,
                WalletCaller.PLATFORM,
                WalletOperationKind.DEPOSIT,
                UUID.fromString("019b76da-a000-7000-8000-000000000031"),
                AMOUNT),
            identity(
                KEY, WalletCaller.PLATFORM, WalletOperationKind.DEPOSIT, USER, Money.krw(101L)));

    assertThat(baseline.requireMatching(matching)).isSameAs(matching);
    conflicts.forEach(
        conflict ->
            assertThatThrownBy(() -> conflict.requireMatching(matching))
                .isInstanceOf(IdempotencyConflictException.class));
    assertThatThrownBy(() -> baseline.requireMatching(outcome(KEY, "f".repeat(64))))
        .isInstanceOf(IdempotencyConflictException.class);
  }

  private static WalletRequestIdentity identity(
      IdempotencyKey key,
      WalletCaller caller,
      WalletOperationKind kind,
      UUID userId,
      Money amount) {
    return new WalletRequestIdentity(key, caller, kind, userId, amount);
  }

  private static WalletOperation outcome(IdempotencyKey key, String fingerprint) {
    return WalletOperation.succeeded(
        key,
        WalletCaller.PLATFORM,
        WalletOperationKind.DEPOSIT,
        USER,
        AMOUNT,
        fingerprint,
        UUID.fromString("019b76da-a000-7000-8000-000000000032"),
        Instant.parse("2026-01-01T00:00:00Z"));
  }
}
