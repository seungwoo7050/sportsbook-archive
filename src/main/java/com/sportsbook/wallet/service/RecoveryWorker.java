package com.sportsbook.wallet.service;

import com.sportsbook.wallet.domain.Account;
import com.sportsbook.wallet.domain.WalletAdjustment;
import com.sportsbook.wallet.domain.WalletOperation;
import com.sportsbook.wallet.persistence.AccountRepository;
import com.sportsbook.wallet.persistence.DatabaseClock;
import com.sportsbook.wallet.persistence.WalletAdjustmentRepository;
import com.sportsbook.wallet.persistence.WalletOperationRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Claims and processes at most one due account in its own database transaction. */
@Component
public class RecoveryWorker {
  private static final int TRANSACTION_TIMEOUT_SECONDS = 5;

  public enum Result {
    NO_WORK,
    APPLIED,
    DEFERRED_FUNDS
  }

  private final AccountRepository accounts;
  private final WalletAdjustmentRepository adjustments;
  private final WalletOperationRepository operations;
  private final DatabaseClock databaseClock;
  private final RecoveryHeadProcessor processor;
  private final TransactionTemplate transaction;

  public RecoveryWorker(
      AccountRepository accounts,
      WalletAdjustmentRepository adjustments,
      WalletOperationRepository operations,
      DatabaseClock databaseClock,
      RecoveryHeadProcessor processor,
      PlatformTransactionManager transactionManager) {
    this.accounts = accounts;
    this.adjustments = adjustments;
    this.operations = operations;
    this.databaseClock = databaseClock;
    this.processor = processor;
    this.transaction = new TransactionTemplate(transactionManager);
    this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.transaction.setTimeout(TRANSACTION_TIMEOUT_SECONDS);
  }

  public Result recoverOne() {
    return transaction.execute(status -> recoverLocked());
  }

  private Result recoverLocked() {
    Optional<Account> candidate = accounts.lockNextDueRecoveryAccount();
    if (candidate.isEmpty()) {
      return Result.NO_WORK;
    }
    Account account = candidate.orElseThrow();
    WalletAdjustment proof =
        adjustments
            .findOldestBlockedForUpdate(account.userId())
            .orElseThrow(() -> new IllegalStateException("Recovery debt has no FIFO head"));
    WalletOperation operation =
        operations
            .findByIdForUpdate(proof.idempotencyKey())
            .orElseThrow(() -> new IllegalStateException("Recovery proof has no operation"));
    RecoveryClaim claim = RecoveryClaim.locked(account, proof, operation);
    return switch (processor.process(claim, databaseClock.now())) {
      case APPLIED -> Result.APPLIED;
      case DEFERRED_FUNDS -> Result.DEFERRED_FUNDS;
    };
  }
}
