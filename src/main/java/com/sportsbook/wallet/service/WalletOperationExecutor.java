package com.sportsbook.wallet.service;

import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.wallet.domain.WalletCaller;
import com.sportsbook.wallet.domain.WalletOperation;
import com.sportsbook.wallet.domain.WalletOperationKind;
import com.sportsbook.wallet.persistence.IdempotencyKeyLock;
import com.sportsbook.wallet.persistence.PostgresFailureTranslator;
import com.sportsbook.wallet.persistence.WalletOperationRepository;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Executes first writers under a key lock and replays immutable durable outcomes without locks. */
@Component
public class WalletOperationExecutor {

  private final WalletOperationRepository operations;
  private final IdempotencyKeyLock keyLocks;
  private final TransactionTemplate writeTransaction;

  public WalletOperationExecutor(
      WalletOperationRepository operations,
      IdempotencyKeyLock keyLocks,
      TransactionTemplate writeTransaction) {
    this.operations = operations;
    this.keyLocks = keyLocks;
    this.writeTransaction = writeTransaction;
  }

  @SuppressWarnings("checkstyle:ParameterNumber")
  public WalletOperation execute(
      IdempotencyKey key,
      WalletCaller caller,
      WalletOperationKind kind,
      UUID userId,
      Money amount,
      Function<String, WalletOperation> firstWriter) {
    Objects.requireNonNull(firstWriter, "firstWriter");
    WalletRequestIdentity request = new WalletRequestIdentity(key, caller, kind, userId, amount);
    if (TransactionSynchronizationManager.isActualTransactionActive()) {
      throw new IllegalStateException("Wallet operations require a non-transactional caller");
    }

    Optional<WalletOperation> replay = findOutcome(key);
    if (replay.isPresent()) {
      return request.requireMatching(replay.get());
    }

    try {
      return Objects.requireNonNull(
          writeTransaction.execute(
              ignored -> {
                keyLocks.acquire(key);
                String fingerprint = request.fingerprint();
                Optional<WalletOperation> winner = findOutcome(key);
                if (winner.isPresent()) {
                  return request.requireMatching(winner.get());
                }
                WalletOperation created =
                    Objects.requireNonNull(firstWriter.apply(fingerprint), "firstWriter outcome");
                request.requireMatching(created);
                return operations.saveAndFlush(created);
              }));
    } catch (RuntimeException failedAttempt) {
      Optional<WalletOperation> winner = findOutcome(key);
      if (winner.isEmpty()) {
        throw PostgresFailureTranslator.translate(key, failedAttempt);
      }
      return request.requireMatching(winner.get());
    }
  }

  private Optional<WalletOperation> findOutcome(IdempotencyKey key) {
    try {
      return operations.findById(key.value());
    } catch (RuntimeException failure) {
      throw PostgresFailureTranslator.translate(key, failure);
    }
  }
}
