package com.sportsbook.wallet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.wallet.domain.Account;
import com.sportsbook.wallet.domain.BalanceBucket;
import com.sportsbook.wallet.domain.LedgerEntry;
import com.sportsbook.wallet.domain.LedgerReason;
import com.sportsbook.wallet.domain.SystemAccountIds;
import com.sportsbook.wallet.service.command.AdjustmentCommand;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdjustmentTransfersTest {
  private static final UUID USER_ID = UUID.fromString("019b76da-a000-7000-8000-000000000121");
  private static final UUID BET_ID = UUID.fromString("019b76da-a000-7000-8000-000000000122");
  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  @Test
  void creditsAnIncreaseFromTheHousePool() {
    Account account = Account.openFor(USER_ID, Currency.KRW, NOW);

    WalletTransferPlan plan = AdjustmentTransfers.increase(account, command(100L, 130L), NOW);

    assertThat(account.available()).isEqualTo(Money.krw(30L));
    assertThat(plan.destination())
        .isEqualTo(new LedgerEntry.TransferLeg(USER_ID, BalanceBucket.AVAILABLE));
    assertThat(plan.source())
        .isEqualTo(new LedgerEntry.TransferLeg(SystemAccountIds.HOUSE, BalanceBucket.AVAILABLE));
    assertThat(plan.reason()).isEqualTo(LedgerReason.BET_ADJUSTMENT);
  }

  @Test
  void debitsAnAffordableDecreaseToTheHousePool() {
    Account account = Account.openFor(USER_ID, Currency.KRW, NOW);
    account.increaseAvailable(Money.krw(50L), NOW);

    WalletTransferPlan plan = AdjustmentTransfers.decrease(account, command(130L, 100L), NOW);

    assertThat(account.available()).isEqualTo(Money.krw(20L));
    assertThat(plan.destination())
        .isEqualTo(new LedgerEntry.TransferLeg(SystemAccountIds.HOUSE, BalanceBucket.AVAILABLE));
    assertThat(plan.source())
        .isEqualTo(new LedgerEntry.TransferLeg(USER_ID, BalanceBucket.AVAILABLE));
  }

  @Test
  void rejectsACommandRoutedToTheWrongTransferDirection() {
    Account account = Account.openFor(USER_ID, Currency.KRW, NOW);

    assertThatThrownBy(() -> AdjustmentTransfers.increase(account, command(130L, 100L), NOW))
        .hasMessage("Increase transfer requires a positive delta");
    assertThatThrownBy(() -> AdjustmentTransfers.decrease(account, command(100L, 130L), NOW))
        .hasMessage("Decrease transfer requires a negative delta");
  }

  private AdjustmentCommand command(long previous, long next) {
    UUID revisionId = UUID.randomUUID();
    return new AdjustmentCommand(
        revisionId,
        BET_ID,
        1L,
        USER_ID,
        Money.krw(previous),
        Money.krw(next),
        IdempotencyKey.of("settlement:revision:" + revisionId));
  }
}
