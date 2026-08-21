package com.sportsbook.wallet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.wallet.domain.BalanceBucket;
import com.sportsbook.wallet.domain.LedgerEntry;
import com.sportsbook.wallet.domain.LedgerReason;
import com.sportsbook.wallet.domain.SystemAccountIds;
import com.sportsbook.wallet.domain.WalletCaller;
import com.sportsbook.wallet.domain.WalletFailureCode;
import com.sportsbook.wallet.domain.WalletFailureSnapshot;
import com.sportsbook.wallet.domain.WalletOperation;
import com.sportsbook.wallet.domain.WalletOperationKind;
import com.sportsbook.wallet.domain.error.WalletRejectedException;
import com.sportsbook.wallet.persistence.LedgerEntryRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WalletOutcomeResolverTest {
  @Mock LedgerEntryRepository ledger;
  @InjectMocks WalletOutcomeResolver resolver;

  @Test
  void resolvesSucceededRejectedAndBlockedOutcomes() {
    UUID userId = UUID.fromString("019b76da-a000-7000-8000-000000000022");
    UUID groupId = UUID.fromString("019b76da-a000-7000-8000-000000000023");
    Instant now = Instant.parse("2026-01-04T00:00:00Z");
    Money amount = Money.krw(75L);
    IdempotencyKey successKey = IdempotencyKey.of("resolver:success");
    LedgerEntry.Pair pair =
        LedgerEntry.pair(
            new LedgerEntry.TransferLeg(userId, BalanceBucket.AVAILABLE),
            new LedgerEntry.TransferLeg(SystemAccountIds.EXTERNAL_PAYMENT, BalanceBucket.AVAILABLE),
            amount,
            LedgerReason.DEPOSIT,
            successKey,
            groupId,
            now);
    WalletOperation succeeded =
        WalletOperation.succeeded(
            successKey,
            WalletCaller.PLATFORM,
            WalletOperationKind.DEPOSIT,
            userId,
            amount,
            "a".repeat(64),
            groupId,
            now);
    when(ledger.findByOperationGroupId(groupId)).thenReturn(List.of(pair.debit(), pair.credit()));

    assertThat(resolver.resolve(succeeded).operationGroupId()).isEqualTo(groupId);

    WalletFailureSnapshot failure =
        WalletFailureSnapshot.withBalance(
            WalletFailureCode.INSUFFICIENT_BALANCE, "available 0", Money.krw(0L));
    WalletOperation rejected =
        WalletOperation.rejected(
            IdempotencyKey.of("resolver:rejected"),
            WalletCaller.PLATFORM,
            WalletOperationKind.WITHDRAW,
            userId,
            amount,
            "b".repeat(64),
            failure,
            now);
    assertThatThrownBy(() -> resolver.resolve(rejected))
        .isInstanceOf(WalletRejectedException.class)
        .extracting("failure")
        .isSameAs(failure);

    WalletOperation blocked =
        WalletOperation.blockedFunds(
            IdempotencyKey.of("resolver:blocked"),
            WalletCaller.SETTLEMENT,
            userId,
            amount,
            "c".repeat(64),
            now);
    assertThatThrownBy(() -> resolver.resolve(blocked))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Blocked");
  }
}
