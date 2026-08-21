package com.sportsbook.wallet.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.Money;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WalletOperationTransitionTest {
  private static final UUID USER_ID = UUID.fromString("019b76da-a000-7000-8000-000000000022");
  private static final UUID GROUP_ID = UUID.fromString("019b76da-a000-7000-8000-000000000023");
  private static final Instant REQUESTED_AT = Instant.parse("2026-01-01T00:00:00Z");

  @Test
  void allowsOneBlockedCompletion() {
    WalletOperation operation = blockedOperation();
    Instant completedAt = REQUESTED_AT.minusNanos(1L);

    operation.completeBlocked(GROUP_ID, completedAt);

    assertThat(operation.status()).isEqualTo(WalletOperationStatus.SUCCEEDED);
    assertThat(operation.operationGroupId()).isEqualTo(GROUP_ID);
    assertThat(operation.updatedAt()).isEqualTo(completedAt);
    assertThat(operation.completedAt()).isEqualTo(completedAt);
    assertThatIllegalStateException()
        .isThrownBy(
            () ->
                operation.completeBlocked(
                    UUID.fromString("019b76da-a000-7000-8000-000000000024"), completedAt));
  }

  @Test
  void rejectsMissingGroupsAndTerminalCompletions() {
    WalletOperation missingGroup = blockedOperation();
    assertThatNullPointerException()
        .isThrownBy(() -> missingGroup.completeBlocked(null, REQUESTED_AT.plusSeconds(1L)));
    assertThat(missingGroup.status()).isEqualTo(WalletOperationStatus.BLOCKED_FUNDS);
    assertThat(missingGroup.operationGroupId()).isNull();
    assertThat(missingGroup.updatedAt()).isEqualTo(REQUESTED_AT);
    assertThat(missingGroup.completedAt()).isNull();

    WalletOperation terminal =
        WalletOperation.succeeded(
            IdempotencyKey.of("deposit:terminal"),
            WalletCaller.PLATFORM,
            WalletOperationKind.DEPOSIT,
            USER_ID,
            Money.krw(1L),
            "a".repeat(64),
            GROUP_ID,
            REQUESTED_AT);
    assertThatIllegalStateException()
        .isThrownBy(() -> terminal.completeBlocked(GROUP_ID, REQUESTED_AT));
  }

  private static WalletOperation blockedOperation() {
    return WalletOperation.blockedFunds(
        IdempotencyKey.of("adjustment:blocked"),
        WalletCaller.SETTLEMENT,
        USER_ID,
        Money.krw(10L),
        "b".repeat(64),
        REQUESTED_AT);
  }
}
