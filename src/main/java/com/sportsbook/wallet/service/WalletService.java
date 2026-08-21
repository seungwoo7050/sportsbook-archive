package com.sportsbook.wallet.service;

import com.sportsbook.wallet.domain.Account;
import com.sportsbook.wallet.domain.BalanceBucket;
import com.sportsbook.wallet.domain.LedgerEntry;
import com.sportsbook.wallet.domain.LedgerReason;
import com.sportsbook.wallet.domain.SystemAccountIds;
import com.sportsbook.wallet.domain.WalletCaller;
import com.sportsbook.wallet.domain.WalletOperationKind;
import com.sportsbook.wallet.domain.error.AccountNotFoundException;
import com.sportsbook.wallet.domain.error.CurrencyMismatchException;
import com.sportsbook.wallet.persistence.AccountRepository;
import com.sportsbook.wallet.service.command.DepositCommand;
import com.sportsbook.wallet.service.command.OpenAccountCommand;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Application facade for account lifecycle and wallet transfers. */
@Service
public class WalletService {

  private final AccountRepository accounts;
  private final TransactionTemplate writeTransaction;
  private final Clock clock;
  private final WalletTransferExecutor transferExecutor;

  public WalletService(
      AccountRepository accounts,
      TransactionTemplate writeTransaction,
      Clock clock,
      WalletTransferExecutor transferExecutor) {
    this.accounts = accounts;
    this.writeTransaction = writeTransaction;
    this.clock = clock;
    this.transferExecutor = transferExecutor;
  }

  public Account openAccount(OpenAccountCommand command) {
    Optional<Account> existing = accounts.findById(command.userId());
    if (existing.isPresent()) {
      return requireCurrency(existing.get(), command);
    }

    Account fresh = Account.openFor(command.userId(), command.currency(), databaseTimestamp());
    try {
      return writeTransaction.execute(ignored -> accounts.saveAndFlush(fresh));
    } catch (DataIntegrityViolationException raceLost) {
      Account winner =
          accounts
              .findById(command.userId())
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "Account race committed no winner for " + command.userId(), raceLost));
      return requireCurrency(winner, command);
    }
  }

  public Account requireAccount(UUID userId) {
    return accounts.findById(userId).orElseThrow(() -> new AccountNotFoundException(userId));
  }

  public WalletOperationResult deposit(DepositCommand command) {
    return transferExecutor.execute(
        command.idempotencyKey(),
        WalletCaller.PLATFORM,
        WalletOperationKind.DEPOSIT,
        command.userId(),
        command.amount(),
        (account, now) -> {
          account.increaseAvailable(command.amount(), now);
          return new WalletTransferPlan(
              new LedgerEntry.TransferLeg(command.userId(), BalanceBucket.AVAILABLE),
              new LedgerEntry.TransferLeg(
                  SystemAccountIds.EXTERNAL_PAYMENT, BalanceBucket.AVAILABLE),
              LedgerReason.DEPOSIT);
        });
  }

  private static Account requireCurrency(Account account, OpenAccountCommand command) {
    if (account.currency() != command.currency()) {
      throw new CurrencyMismatchException(account.currency(), command.currency());
    }
    return account;
  }

  private Instant databaseTimestamp() {
    return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
  }
}
