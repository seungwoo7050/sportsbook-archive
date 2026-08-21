package com.sportsbook.wallet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.wallet.domain.Account;
import com.sportsbook.wallet.domain.WalletAdjustment;
import com.sportsbook.wallet.domain.WalletCaller;
import com.sportsbook.wallet.domain.WalletOperation;
import com.sportsbook.wallet.persistence.AccountRepository;
import com.sportsbook.wallet.persistence.DatabaseClock;
import com.sportsbook.wallet.persistence.WalletAdjustmentRepository;
import com.sportsbook.wallet.persistence.WalletOperationRepository;
import com.sportsbook.wallet.service.command.AdjustmentCommand;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

class RecoveryWorkerLockOrderTest {
  private static final UUID USER_ID = UUID.fromString("019b76da-a000-7000-8000-000000000187");
  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  @Test
  void locksAccountThenHeadThenOperationBeforeReadingDatabaseTime() {
    AccountRepository accounts = mock(AccountRepository.class);
    WalletAdjustmentRepository adjustments = mock(WalletAdjustmentRepository.class);
    WalletOperationRepository operations = mock(WalletOperationRepository.class);
    DatabaseClock clock = mock(DatabaseClock.class);
    RecoveryHeadProcessor processor = mock(RecoveryHeadProcessor.class);
    PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    when(transactions.getTransaction(any(TransactionDefinition.class)))
        .thenReturn(mock(TransactionStatus.class));
    AdjustmentCommand command = command();
    Account account = Account.openFor(USER_ID, Money.krw(0L).currency(), NOW);
    account.queueRecoveryDebt(command.absoluteDelta(), NOW);
    WalletAdjustment proof = WalletAdjustment.blocked(command, 1L, NOW);
    WalletOperation operation = blockedOperation(command);
    when(accounts.lockNextDueRecoveryAccount()).thenReturn(Optional.of(account));
    when(adjustments.findOldestBlockedForUpdate(USER_ID)).thenReturn(Optional.of(proof));
    when(operations.findByIdForUpdate(command.idempotencyKey().value()))
        .thenReturn(Optional.of(operation));
    when(clock.now()).thenReturn(NOW.plusSeconds(1L));
    when(processor.process(any(RecoveryClaim.class), any(Instant.class)))
        .thenReturn(RecoveryHeadProcessor.Result.APPLIED);
    RecoveryWorker worker =
        new RecoveryWorker(accounts, adjustments, operations, clock, processor, transactions);

    assertThat(worker.recoverOne()).isEqualTo(RecoveryWorker.Result.APPLIED);

    InOrder order = inOrder(accounts, adjustments, operations, clock, processor);
    order.verify(accounts).lockNextDueRecoveryAccount();
    order.verify(adjustments).findOldestBlockedForUpdate(USER_ID);
    order.verify(operations).findByIdForUpdate(command.idempotencyKey().value());
    order.verify(clock).now();
    order.verify(processor).process(any(RecoveryClaim.class), any(Instant.class));
    ArgumentCaptor<TransactionDefinition> definition =
        ArgumentCaptor.forClass(TransactionDefinition.class);
    verify(transactions).getTransaction(definition.capture());
    assertThat(definition.getValue().getTimeout()).isEqualTo(5);
    assertThat(definition.getValue().getPropagationBehavior())
        .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  private AdjustmentCommand command() {
    UUID revisionId = UUID.fromString("019b76da-a000-7000-8000-000000000188");
    return new AdjustmentCommand(
        revisionId,
        UUID.fromString("019b76da-a000-7000-8000-000000000189"),
        1L,
        USER_ID,
        Money.krw(100L),
        Money.krw(0L),
        IdempotencyKey.of("settlement:revision:" + revisionId));
  }

  private WalletOperation blockedOperation(AdjustmentCommand command) {
    return WalletOperation.blockedFunds(
        command.idempotencyKey(),
        WalletCaller.SETTLEMENT,
        USER_ID,
        command.absoluteDelta(),
        "d".repeat(64),
        NOW);
  }
}
