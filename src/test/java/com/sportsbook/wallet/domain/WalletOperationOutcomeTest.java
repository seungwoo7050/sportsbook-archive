package com.sportsbook.wallet.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.Money;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WalletOperationOutcomeTest {
  private static final UUID USER_ID = UUID.fromString("019b76da-a000-7000-8000-000000000020");
  private static final UUID GROUP_ID = UUID.fromString("019b76da-a000-7000-8000-000000000021");
  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  @Test
  void constructsSucceededOutcomes() {
    WalletOperation operation =
        WalletOperation.succeeded(
            IdempotencyKey.of("deposit:outcome"),
            WalletCaller.PLATFORM,
            WalletOperationKind.DEPOSIT,
            USER_ID,
            Money.krw(100L),
            "a".repeat(64),
            GROUP_ID,
            NOW);

    assertThat(operation.status()).isEqualTo(WalletOperationStatus.SUCCEEDED);
    assertThat(operation.operationGroupId()).isEqualTo(GROUP_ID);
    assertThat(operation.failure()).isNull();
    assertThat(operation.requestedAt()).isEqualTo(NOW);
    assertThat(operation.updatedAt()).isEqualTo(NOW);
    assertThat(operation.completedAt()).isEqualTo(NOW);
  }

  @Test
  void constructsRejectedAndBlockedOutcomes() {
    WalletFailureSnapshot failure =
        WalletFailureSnapshot.of(WalletFailureCode.ACCOUNT_NOT_FOUND, "missing");
    WalletOperation rejected =
        WalletOperation.rejected(
            IdempotencyKey.of("withdraw:outcome"),
            WalletCaller.PLATFORM,
            WalletOperationKind.WITHDRAW,
            USER_ID,
            Money.krw(10L),
            "b".repeat(64),
            failure,
            NOW);
    WalletOperation blocked =
        WalletOperation.blockedFunds(
            IdempotencyKey.of("adjustment:outcome"),
            WalletCaller.SETTLEMENT,
            USER_ID,
            Money.krw(10L),
            "c".repeat(64),
            NOW);

    assertThat(rejected.status()).isEqualTo(WalletOperationStatus.REJECTED);
    assertThat(rejected.failure()).isSameAs(failure);
    assertThat(rejected.operationGroupId()).isNull();
    assertThat(rejected.completedAt()).isEqualTo(NOW);
    assertThat(blocked.status()).isEqualTo(WalletOperationStatus.BLOCKED_FUNDS);
    assertThat(blocked.operationGroupId()).isNull();
    assertThat(blocked.completedAt()).isNull();
  }

  @Test
  void rejectsNonPositiveOperationAmounts() {
    for (Money amount : new Money[] {Money.zero(Currency.KRW), Money.krw(-1L)}) {
      assertThatIllegalArgumentException()
          .isThrownBy(
              () ->
                  WalletOperation.blockedFunds(
                      IdempotencyKey.of("adjustment:invalid"),
                      WalletCaller.SETTLEMENT,
                      USER_ID,
                      amount,
                      "d".repeat(64),
                      NOW));
    }
  }
}
