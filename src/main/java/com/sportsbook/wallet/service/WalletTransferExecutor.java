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
import com.sportsbook.wallet.persistence.AccountRepository;
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
  private final Clock clock;

  public WalletTransferExecutor(
      AccountRepository accounts,
      WalletOperationExecutor operations,
      WalletTransferWriter transfers,
      WalletOutcomeResolver outcomes,
      Clock clock) {
    this.accounts = accounts;
    this.operations = operations;
    this.transfers = transfers;
    this.outcomes = outcomes;
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
    WalletOperation operation =
        operations.execute(
            key,
            caller,
            kind,
            userId,
            amount,
            fingerprint -> firstWrite(key, caller, kind, userId, amount, fingerprint, mutation));
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
      BiFunction<Account, Instant, WalletTransferPlan> mutation) {
    Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
    try {
      Account account = lockAccount(userId, amount);
      WalletTransferPlan plan = mutation.apply(account, now);
      WalletOperationResult result =
          transfers.write(
              plan.destination(), plan.source(), amount, plan.reason(), key, userId, now);
      return WalletOperation.succeeded(
          key, caller, kind, userId, amount, fingerprint, result.operationGroupId(), now);
    } catch (RuntimeException businessOrInfrastructure) {
      WalletFailureSnapshot failure =
          WalletFailureMapper.snapshot(businessOrInfrastructure, amount);
      return WalletOperation.rejected(key, caller, kind, userId, amount, fingerprint, failure, now);
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
