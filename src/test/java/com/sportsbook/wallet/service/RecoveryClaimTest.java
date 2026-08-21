package com.sportsbook.wallet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.wallet.domain.Account;
import com.sportsbook.wallet.domain.WalletAdjustment;
import com.sportsbook.wallet.domain.WalletCaller;
import com.sportsbook.wallet.domain.WalletOperation;
import com.sportsbook.wallet.service.command.AdjustmentCommand;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecoveryClaimTest {
  private static final UUID USER_ID = UUID.fromString("019b76da-a000-7000-8000-000000000180");
  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  @Test
  void acceptsOneConsistentLockedClaim() {
    AdjustmentCommand command = command();
    Account account = Account.openFor(USER_ID, Money.krw(0L).currency(), NOW);
    account.queueRecoveryDebt(command.absoluteDelta(), NOW);
    WalletAdjustment proof = WalletAdjustment.blocked(command, 1L, NOW);
    WalletOperation operation = blockedOperation(command, command.absoluteDelta());

    RecoveryClaim claim = RecoveryClaim.locked(account, proof, operation);

    assertThat(claim.amount()).isEqualTo(Money.krw(300L));
    assertThat(claim.account()).isSameAs(account);
  }

  @Test
  void rejectsAnOperationWithDifferentRequestMoney() {
    AdjustmentCommand command = command();
    Account account = Account.openFor(USER_ID, Money.krw(0L).currency(), NOW);
    account.queueRecoveryDebt(command.absoluteDelta(), NOW);

    assertThatThrownBy(
            () ->
                RecoveryClaim.locked(
                    account,
                    WalletAdjustment.blocked(command, 1L, NOW),
                    blockedOperation(command, Money.krw(299L))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("amount");
  }

  private AdjustmentCommand command() {
    UUID revisionId = UUID.fromString("019b76da-a000-7000-8000-000000000181");
    return new AdjustmentCommand(
        revisionId,
        UUID.fromString("019b76da-a000-7000-8000-000000000182"),
        1L,
        USER_ID,
        Money.krw(500L),
        Money.krw(200L),
        IdempotencyKey.of("settlement:revision:" + revisionId));
  }

  private WalletOperation blockedOperation(AdjustmentCommand command, Money amount) {
    return WalletOperation.blockedFunds(
        command.idempotencyKey(),
        WalletCaller.SETTLEMENT,
        command.userId(),
        amount,
        "a".repeat(64),
        NOW);
  }
}
