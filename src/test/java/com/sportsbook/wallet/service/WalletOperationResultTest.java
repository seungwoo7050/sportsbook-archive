package com.sportsbook.wallet.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.wallet.domain.BalanceBucket;
import com.sportsbook.wallet.domain.LedgerEntry;
import com.sportsbook.wallet.domain.LedgerReason;
import com.sportsbook.wallet.domain.SystemAccountIds;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WalletOperationResultTest {

  private static final UUID USER = UUID.fromString("019b76da-a000-7000-8000-000000000101");
  private static final UUID GROUP = UUID.fromString("019b76da-a000-7000-8000-000000000201");
  private static final Money AMOUNT = Money.krw(10_000L);
  private static final IdempotencyKey KEY = IdempotencyKey.of("durable-result");
  private static final Instant AT = Instant.parse("2026-07-14T00:00:00Z");

  @Test
  void rejectsPartialAndDuplicateSideResults() {
    LedgerEntry.Pair pair = transfer(USER, SystemAccountIds.HOUSE, GROUP);

    assertThatThrownBy(() -> WalletOperationResult.fromExisting(List.of(pair.debit())))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("exactly two");
    assertThatThrownBy(
            () -> WalletOperationResult.fromExisting(List.of(pair.debit(), pair.debit())))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("one DEBIT and one CREDIT");
  }

  @Test
  void rejectsRowsFromDifferentTransfersOrUsers() {
    LedgerEntry.Pair first = transfer(USER, SystemAccountIds.HOUSE, GROUP);
    LedgerEntry.Pair otherGroup =
        transfer(
            USER, SystemAccountIds.HOUSE, UUID.fromString("019b76da-a000-7000-8000-000000000202"));
    LedgerEntry.Pair twoUsers =
        transfer(USER, UUID.fromString("019b76da-a000-7000-8000-000000000102"), GROUP);

    assertThatThrownBy(
            () -> WalletOperationResult.fromExisting(List.of(first.debit(), otherGroup.credit())))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("one matched transfer");
    assertThatThrownBy(
            () -> WalletOperationResult.fromExisting(List.of(twoUsers.debit(), twoUsers.credit())))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("exactly one user account");
  }

  private static LedgerEntry.Pair transfer(UUID destination, UUID source, UUID groupId) {
    return LedgerEntry.pair(
        new LedgerEntry.TransferLeg(destination, BalanceBucket.AVAILABLE),
        new LedgerEntry.TransferLeg(source, BalanceBucket.AVAILABLE),
        AMOUNT,
        LedgerReason.BET_PAYOUT,
        KEY,
        groupId,
        AT);
  }
}
