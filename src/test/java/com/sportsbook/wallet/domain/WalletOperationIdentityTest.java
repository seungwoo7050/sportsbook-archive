package com.sportsbook.wallet.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.Money;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WalletOperationIdentityTest {

  @Test
  void preservesImmutableRequestIdentity() {
    IdempotencyKey key = IdempotencyKey.of("deposit:identity");
    UUID userId = UUID.fromString("019b76da-a000-7000-8000-000000000025");
    String fingerprint = "f".repeat(64);
    WalletOperation operation =
        WalletOperation.succeeded(
            key,
            WalletCaller.PLATFORM,
            WalletOperationKind.DEPOSIT,
            userId,
            Money.krw(100L),
            fingerprint,
            UUID.fromString("019b76da-a000-7000-8000-000000000026"),
            Instant.parse("2026-01-01T00:00:00Z"));

    assertThat(operation.idempotencyKey()).isEqualTo(key.value());
    assertThat(operation.caller()).isEqualTo(WalletCaller.PLATFORM);
    assertThat(operation.kind()).isEqualTo(WalletOperationKind.DEPOSIT);
    assertThat(operation.userId()).isEqualTo(userId);
    assertThat(operation.requestAmount()).isEqualTo(Money.krw(100L));
    assertThat(operation.requestFingerprint()).isEqualTo(fingerprint);
  }
}
