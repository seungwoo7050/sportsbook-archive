package com.sportsbook.wallet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.wallet.domain.Account;
import com.sportsbook.wallet.domain.WalletAdjustment;
import com.sportsbook.wallet.domain.WalletOperation;
import com.sportsbook.wallet.persistence.AccountRepository;
import com.sportsbook.wallet.persistence.AdjustmentPairLock;
import com.sportsbook.wallet.persistence.DatabaseClock;
import com.sportsbook.wallet.persistence.WalletAdjustmentRepository;
import com.sportsbook.wallet.service.command.AdjustmentCommand;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdjustmentFirstWriterTest {
  private static final UUID USER_ID = UUID.fromString("019b76da-a000-7000-8000-000000000131");
  private static final UUID BET_ID = UUID.fromString("019b76da-a000-7000-8000-000000000132");
  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  @Mock AdjustmentPairLock pairLocks;
  @Mock WalletAdjustmentRepository adjustments;
  @Mock AccountRepository accounts;
  @Mock DatabaseClock databaseClock;
  @Mock AdjustmentProofWriter proofWriter;
  @Mock Account account;
  @Mock WalletAdjustment head;
  @Mock WalletOperation outcome;
  @InjectMocks AdjustmentFirstWriter writer;

  @Test
  void locksPairAccountAndHeadBeforeApplyingAFrozenAccountIncrease() {
    AdjustmentCommand command = command(700L, 1_000L);
    when(adjustments.findByBetIdAndRevisionNumber(BET_ID, 1L)).thenReturn(Optional.empty());
    when(accounts.findByUserIdForUpdate(USER_ID)).thenReturn(Optional.of(account));
    when(account.currency()).thenReturn(Currency.KRW);
    when(account.isOutboundFrozen()).thenReturn(true);
    when(adjustments.findOldestBlockedForUpdate(USER_ID)).thenReturn(Optional.of(head));
    when(databaseClock.now()).thenReturn(NOW);
    when(proofWriter.applyIncrease(command, "a".repeat(64), account, Optional.of(head), NOW))
        .thenReturn(outcome);

    assertThat(writer.write(command, "a".repeat(64))).isSameAs(outcome);

    InOrder locks = inOrder(pairLocks, adjustments, accounts);
    locks.verify(pairLocks).acquire(BET_ID, 1L);
    locks.verify(adjustments).findByBetIdAndRevisionNumber(BET_ID, 1L);
    locks.verify(accounts).findByUserIdForUpdate(USER_ID);
    locks.verify(adjustments).findOldestBlockedForUpdate(USER_ID);
  }

  @Test
  void queuesANegativeCorrectionBehindAnExistingHeadDespiteSufficientFunds() {
    AdjustmentCommand command = command(1_000L, 700L);
    when(adjustments.findByBetIdAndRevisionNumber(BET_ID, 1L)).thenReturn(Optional.empty());
    when(accounts.findByUserIdForUpdate(USER_ID)).thenReturn(Optional.of(account));
    when(account.currency()).thenReturn(Currency.KRW);
    when(account.isOutboundFrozen()).thenReturn(true);
    when(adjustments.findOldestBlockedForUpdate(USER_ID)).thenReturn(Optional.of(head));
    when(databaseClock.now()).thenReturn(NOW);
    when(proofWriter.block(command, "b".repeat(64), account, NOW)).thenReturn(outcome);

    assertThat(writer.write(command, "b".repeat(64))).isSameAs(outcome);

    verify(proofWriter).block(command, "b".repeat(64), account, NOW);
  }

  private AdjustmentCommand command(long previous, long next) {
    UUID revisionId = UUID.fromString("019b76da-a000-7000-8000-000000000133");
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
