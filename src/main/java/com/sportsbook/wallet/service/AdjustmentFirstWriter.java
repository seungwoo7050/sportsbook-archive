package com.sportsbook.wallet.service;

import com.sportsbook.wallet.domain.Account;
import com.sportsbook.wallet.domain.WalletAdjustment;
import com.sportsbook.wallet.domain.WalletOperation;
import com.sportsbook.wallet.domain.error.AccountNotFoundException;
import com.sportsbook.wallet.domain.error.BalanceLimitExceededException;
import com.sportsbook.wallet.domain.error.CurrencyMismatchException;
import com.sportsbook.wallet.domain.error.IdempotencyConflictException;
import com.sportsbook.wallet.persistence.AccountRepository;
import com.sportsbook.wallet.persistence.AdjustmentPairLock;
import com.sportsbook.wallet.persistence.DatabaseClock;
import com.sportsbook.wallet.persistence.WalletAdjustmentRepository;
import com.sportsbook.wallet.service.command.AdjustmentCommand;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Applies a first adjustment writer under the established idempotency-key transaction lock. */
@Component
public class AdjustmentFirstWriter {
  private final AdjustmentPairLock pairLocks;
  private final WalletAdjustmentRepository adjustments;
  private final AccountRepository accounts;
  private final DatabaseClock databaseClock;
  private final AdjustmentProofWriter proofWriter;

  public AdjustmentFirstWriter(
      AdjustmentPairLock pairLocks,
      WalletAdjustmentRepository adjustments,
      AccountRepository accounts,
      DatabaseClock databaseClock,
      AdjustmentProofWriter proofWriter) {
    this.pairLocks = pairLocks;
    this.adjustments = adjustments;
    this.accounts = accounts;
    this.databaseClock = databaseClock;
    this.proofWriter = proofWriter;
  }

  public WalletOperation write(AdjustmentCommand command, String fingerprint) {
    pairLocks.acquire(command.betId(), command.revisionNumber());
    if (adjustments
        .findByBetIdAndRevisionNumber(command.betId(), command.revisionNumber())
        .isPresent()) {
      throw new IdempotencyConflictException(command.idempotencyKey());
    }

    Account account;
    Optional<WalletAdjustment> blockedHead;
    try {
      account =
          accounts
              .findByUserIdForUpdate(command.userId())
              .orElseThrow(() -> new AccountNotFoundException(command.userId()));
      if (account.currency() != command.previousPayout().currency()) {
        throw new CurrencyMismatchException(
            account.currency(), command.previousPayout().currency());
      }
      blockedHead = adjustments.findOldestBlockedForUpdate(command.userId());
      requireConsistentRecovery(account, blockedHead);
    } catch (AccountNotFoundException | CurrencyMismatchException rejected) {
      return proofWriter.reject(command, fingerprint, rejected, databaseClock.now());
    }

    Instant now = databaseClock.now();
    try {
      if (command.deltaAmount() > 0L) {
        return proofWriter.applyIncrease(command, fingerprint, account, blockedHead, now);
      }
      if (blockedHead.isPresent()
          || account.available().amount() < command.absoluteDelta().amount()) {
        return proofWriter.block(command, fingerprint, account, now);
      }
      return proofWriter.applyDecrease(command, fingerprint, account, now);
    } catch (BalanceLimitExceededException rejected) {
      return proofWriter.reject(command, fingerprint, rejected, now);
    }
  }

  private static void requireConsistentRecovery(
      Account account, Optional<WalletAdjustment> blockedHead) {
    if (account.isOutboundFrozen() != blockedHead.isPresent()) {
      throw new IllegalStateException(
          "Recovery debt and FIFO head disagree for " + account.userId());
    }
  }
}
