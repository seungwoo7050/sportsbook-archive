package com.sportsbook.wallet.service;

import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.wallet.domain.Account;
import com.sportsbook.wallet.domain.WalletCaller;
import com.sportsbook.wallet.domain.WalletFailureSnapshot;
import com.sportsbook.wallet.domain.WalletOperation;
import com.sportsbook.wallet.domain.WalletOperationKind;
import com.sportsbook.wallet.domain.error.AccountNotFoundException;
import com.sportsbook.wallet.domain.error.CurrencyMismatchException;
import com.sportsbook.wallet.outbox.OutboxAppender;
import com.sportsbook.wallet.outbox.WalletEventFactory;
import com.sportsbook.wallet.persistence.AccountRepository;
import com.sportsbook.wallet.service.command.CreditCommand;
import com.sportsbook.wallet.service.command.CreditReason;
import com.sportsbook.wallet.service.command.DebitCommand;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.function.BiFunction;
import org.springframework.stereotype.Component;

/** Runs account mutation, matched ledger pair, and authoritative outcome in one transaction. */
@Component
public class WalletTransferExecutor {

  private final AccountRepository accounts;
  private final WalletOperationExecutor operations;
  private final WalletTransferWriter transfers;
  private final WalletOutcomeResolver outcomes;
  private final OutboxAppender outboxAppender;
  private final RecoveryWakeService recoveryWake;
  private final Clock clock;
  private final WalletEventFactory eventFactory = new WalletEventFactory();

  public WalletTransferExecutor(
      AccountRepository accounts,
      WalletOperationExecutor operations,
      WalletTransferWriter transfers,
      WalletOutcomeResolver outcomes,
      OutboxAppender outboxAppender,
      RecoveryWakeService recoveryWake,
      Clock clock) {
    this.accounts = accounts;
    this.operations = operations;
    this.transfers = transfers;
    this.outcomes = outcomes;
    this.outboxAppender = outboxAppender;
    this.recoveryWake = recoveryWake;
    this.clock = clock;
  }

  @SuppressWarnings("checkstyle:ParameterNumber")
  public WalletOperationResult execute(
      IdempotencyKey key,
      WalletCaller caller,
      WalletOperationKind kind,
      UUID userId,
      Money amount,
      BiFunction<Account, Instant, WalletTransferPlan> mutation) {
    return execute(
        key,
        caller,
        kind,
        userId,
        amount,
        OperationFingerprint.transfer(caller, kind, userId, amount),
        null,
        null,
        mutation);
  }

  public WalletOperationResult executeDebit(
      DebitCommand command, BiFunction<Account, Instant, WalletTransferPlan> mutation) {
    return execute(
        command.idempotencyKey(),
        WalletCaller.BETTING,
        WalletOperationKind.BET_DEBIT,
        command.userId(),
        command.amount(),
        OperationFingerprint.transfer(
            WalletCaller.BETTING,
            WalletOperationKind.BET_DEBIT,
            command.userId(),
            command.amount()),
        command,
        null,
        mutation);
  }

  public WalletOperationResult executeCredit(
      WalletCaller caller,
      CreditCommand command,
      BiFunction<Account, Instant, WalletTransferPlan> mutation) {
    requireAllowedCredit(caller, command);
    WalletOperationKind kind =
        command.reason() == CreditReason.PAYOUT
            ? WalletOperationKind.BET_PAYOUT
            : WalletOperationKind.BET_REFUND;
    return execute(
        command.idempotencyKey(),
        caller,
        kind,
        command.userId(),
        command.amount(),
        OperationFingerprint.credit(
            caller, kind, command.userId(), command.amount(), command.source(), command.reason()),
        null,
        command,
        mutation);
  }

  @SuppressWarnings("checkstyle:ParameterNumber")
  private WalletOperationResult execute(
      IdempotencyKey key,
      WalletCaller caller,
      WalletOperationKind kind,
      UUID userId,
      Money amount,
      OperationFingerprint requestFingerprint,
      DebitCommand debit,
      CreditCommand credit,
      BiFunction<Account, Instant, WalletTransferPlan> mutation) {
    WalletOperation operation =
        operations.execute(
            key,
            caller,
            kind,
            userId,
            amount,
            requestFingerprint,
            fingerprint ->
                firstWrite(
                    key, caller, kind, userId, amount, fingerprint, debit, credit, mutation));
    return outcomes.resolve(operation);
  }

  @SuppressWarnings("checkstyle:ParameterNumber")
  private WalletOperation firstWrite(
      IdempotencyKey key,
      WalletCaller caller,
      WalletOperationKind kind,
      UUID userId,
      Money amount,
      String fingerprint,
      DebitCommand debit,
      CreditCommand credit,
      BiFunction<Account, Instant, WalletTransferPlan> mutation) {
    Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
    WalletTransferReceipt receipt;
    try {
      Account account = lockAccount(userId, amount);
      WalletTransferPlan plan = mutation.apply(account, now);
      if (kind == WalletOperationKind.DEPOSIT || credit != null) {
        recoveryWake.wake(account);
      }
      receipt =
          transfers.writeReceipt(
              plan.destination(), plan.source(), amount, plan.reason(), key, userId, now);
    } catch (RuntimeException businessOrInfrastructure) {
      WalletFailureSnapshot failure =
          WalletFailureMapper.snapshot(businessOrInfrastructure, amount);
      if (debit != null) {
        outboxAppender.append(eventFactory.debitFailed(debit, failure, now));
      }
      return WalletOperation.rejected(key, caller, kind, userId, amount, fingerprint, failure, now);
    }
    if (debit != null) {
      outboxAppender.append(eventFactory.debited(debit, receipt.destinationEntryId(), now));
    }
    if (credit != null) {
      outboxAppender.append(eventFactory.credited(credit, receipt.destinationEntryId(), now));
    }
    return WalletOperation.succeeded(
        key, caller, kind, userId, amount, fingerprint, receipt.result().operationGroupId(), now);
  }

  static void requireAllowedCredit(WalletCaller caller, CreditCommand command) {
    boolean allowed =
        (caller == WalletCaller.BETTING
                && command.source() == CreditCommand.Source.USER_LOCKED
                && command.reason() == CreditReason.REFUND)
            || (caller == WalletCaller.SETTLEMENT
                && ((command.source() == CreditCommand.Source.USER_LOCKED
                        && command.reason() != CreditReason.PAYOUT)
                    || (command.source() == CreditCommand.Source.HOUSE_POOL
                        && command.reason() == CreditReason.PAYOUT)))
            || (caller == WalletCaller.ADMIN
                && command.source() == CreditCommand.Source.HOUSE_POOL
                && command.reason() == CreditReason.REFUND);
    if (!allowed) {
      throw new IllegalArgumentException("Caller is not allowed for credit source and reason");
    }
  }

  private Account lockAccount(UUID userId, Money amount) {
    Account account =
        accounts
            .findByUserIdForUpdate(userId)
            .orElseThrow(() -> new AccountNotFoundException(userId));
    if (account.currency() != amount.currency()) {
      throw new CurrencyMismatchException(account.currency(), amount.currency());
    }
    return account;
  }
}
