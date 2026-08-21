package com.sportsbook.wallet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.wallet.domain.Account;
import com.sportsbook.wallet.domain.AdjustmentStatus;
import com.sportsbook.wallet.domain.LedgerReason;
import com.sportsbook.wallet.domain.WalletAdjustment;
import com.sportsbook.wallet.domain.WalletCaller;
import com.sportsbook.wallet.domain.WalletOperation;
import com.sportsbook.wallet.domain.WalletOperationStatus;
import com.sportsbook.wallet.persistence.WalletAdjustmentRepository;
import com.sportsbook.wallet.persistence.WalletOperationRepository;
import com.sportsbook.wallet.service.command.AdjustmentCommand;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecoveryHeadProcessorSuccessTest {
  private static final UUID USER_ID = UUID.fromString("019b76da-a000-7000-8000-000000000186");
  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  @Test
  void fullyCollectsOneHeadAndWakesTheNext() {
    WalletTransferWriter transfers = mock(WalletTransferWriter.class);
    WalletAdjustmentRepository adjustments = mock(WalletAdjustmentRepository.class);
    WalletOperationRepository operations = mock(WalletOperationRepository.class);
    RecoveryHeadProcessor processor =
        new RecoveryHeadProcessor(
            mock(RecoveryRetryPolicy.class), transfers, adjustments, operations);
    AdjustmentCommand first = command("1861", "1862", 1L, 300L);
    AdjustmentCommand second = command("1863", "1864", 2L, 200L);
    Account account = Account.openFor(USER_ID, Money.krw(0L).currency(), NOW);
    account.increaseAvailable(Money.krw(400L), NOW);
    account.queueRecoveryDebt(first.absoluteDelta(), NOW);
    account.queueRecoveryDebt(second.absoluteDelta(), NOW.plusSeconds(1L));
    WalletAdjustment head = WalletAdjustment.blocked(first, 1L, NOW);
    WalletAdjustment next = WalletAdjustment.blocked(second, 2L, NOW.plusSeconds(1L));
    next.deferUntil(NOW.plusSeconds(2L), NOW.plusSeconds(100L));
    WalletOperation operation = blockedOperation(first);
    UUID groupId = UUID.fromString("019b76da-a000-7000-8000-000000001865");
    when(transfers.writeReceipt(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(
            new WalletTransferReceipt(
                new WalletOperationResult(
                    groupId, USER_ID, Money.krw(300L), LedgerReason.BET_ADJUSTMENT, NOW),
                UUID.randomUUID(),
                UUID.randomUUID()));
    when(adjustments.findOldestBlockedForUpdate(USER_ID)).thenReturn(Optional.of(next));

    assertThat(
            processor.process(RecoveryClaim.locked(account, head, operation), NOW.plusSeconds(3L)))
        .isEqualTo(RecoveryHeadProcessor.Result.APPLIED);

    assertThat(head.status()).isEqualTo(AdjustmentStatus.APPLIED);
    assertThat(head.queueSequence()).isEqualTo(1L);
    assertThat(head.nextAttemptAt()).isNull();
    assertThat(operation.status()).isEqualTo(WalletOperationStatus.SUCCEEDED);
    assertThat(account.available()).isEqualTo(Money.krw(100L));
    assertThat(account.recoveryDebtAmount()).isEqualTo(BigInteger.valueOf(200L));
    assertThat(account.isOutboundFrozen()).isTrue();
    assertThat(next.nextAttemptAt()).isEqualTo(NOW.plusSeconds(3L));
  }

  private AdjustmentCommand command(String revisionTail, String betTail, long number, long delta) {
    UUID revisionId = UUID.fromString("019b76da-a000-7000-8000-00000000" + revisionTail);
    return new AdjustmentCommand(
        revisionId,
        UUID.fromString("019b76da-a000-7000-8000-00000000" + betTail),
        number,
        USER_ID,
        Money.krw(delta),
        Money.krw(0L),
        IdempotencyKey.of("settlement:revision:" + revisionId));
  }

  private WalletOperation blockedOperation(AdjustmentCommand command) {
    return WalletOperation.blockedFunds(
        command.idempotencyKey(),
        WalletCaller.SETTLEMENT,
        USER_ID,
        command.absoluteDelta(),
        "c".repeat(64),
        NOW);
  }
}
